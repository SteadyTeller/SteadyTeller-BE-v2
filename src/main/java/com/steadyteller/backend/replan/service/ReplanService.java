package com.steadyteller.backend.replan.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.global.exception.GlobalErrorCode;
import com.steadyteller.backend.learningtask.dto.AiGeneratedTaskDto;
import com.steadyteller.backend.learningtask.dto.CandidateAvailabilityStatusResponseDto;
import com.steadyteller.backend.learningtask.dto.LearningTaskCandidateRequestDto;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.learningtask.entity.LearningTaskStatus;
import com.steadyteller.backend.learningtask.exception.LearningTaskErrorCode;
import com.steadyteller.backend.learningtask.repository.LearningTaskRepository;
import com.steadyteller.backend.learningtask.service.LearningTaskAiService;
import com.steadyteller.backend.member.domain.Availability;
import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.dto.AvailabilityReplaceRequest;
import com.steadyteller.backend.member.dto.AvailabilityResponse;
import com.steadyteller.backend.member.repository.AvailabilityRepository;
import com.steadyteller.backend.member.service.MemberService;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.exception.GoalErrorCode;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.replan.dto.ReplanProposalSummaryResponseDto;
import com.steadyteller.backend.replan.dto.ReplanTaskCandidateResponseDto;
import com.steadyteller.backend.replan.entity.ReplanAvailability;
import com.steadyteller.backend.replan.entity.ReplanProposal;
import com.steadyteller.backend.replan.entity.ReplanTaskCandidate;
import com.steadyteller.backend.replan.repository.ReplanAvailabilityRepository;
import com.steadyteller.backend.replan.repository.ReplanProposalRepository;
import com.steadyteller.backend.replan.repository.ReplanTaskCandidateRepository;
import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.entity.ScheduleItemStatus;
import com.steadyteller.backend.schedule.repository.ScheduleFailureRepository;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import com.steadyteller.backend.schedule.service.ScheduleService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReplanService {
    private final MemberGoalRepository goalRepository;
    private final ReplanProposalRepository proposalRepository;
    private final ReplanAvailabilityRepository replanAvailabilityRepository;
    private final ReplanTaskCandidateRepository candidateRepository;
    private final LearningTaskRepository taskRepository;
    private final LearningTaskAiService aiService;
    private final AvailabilityRepository availabilityRepository;
    private final MemberService memberService;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final ScheduleFailureRepository scheduleFailureRepository;
    private final ScheduleService scheduleService;

    @Transactional
    public ReplanProposalSummaryResponseDto createProposal(Long memberId, Long goalId, AvailabilityReplaceRequest request) {
        MemberGoal goal = getOwnedGoalForUpdate(memberId, goalId);
        List<LearningTask> remaining = taskRepository.findByGoalIdOrderByIdAsc(goalId).stream()
                .filter(task -> remainingMinutes(task) > 0).toList();
        if (!goal.isTaskGenerationLocked() || remaining.isEmpty()) {
            throw new CustomException(GoalErrorCode.REPLAN_NOT_AVAILABLE);
        }
        validateAvailabilityRequests(request);
        proposalRepository.findByGoalId(goalId).ifPresent(this::deleteProposalData);
        ReplanProposal proposal = proposalRepository.save(ReplanProposal.create(goalId));
        List<ReplanAvailability> windows = request.availabilities().stream()
                .map(item -> ReplanAvailability.create(proposal.getId(), item.getDayOfWeek(), item.getStartTime(),
                        item.getEndTime(), item.isEnabledOrDefault())).toList();
        replanAvailabilityRepository.saveAll(windows);

        String remainingContext = remaining.stream().map(task -> task.getTitle() + " (" + remainingMinutes(task) + "분)")
                .reduce("", (left, right) -> left.isBlank() ? right : left + ", " + right);
        String constraint = "재계획 대상의 기존 태스크: " + remainingContext + "\n새 가용시간 총량: "
                + totalMinutes(goal, windows) + "분. 시간이 줄면 부차적인 내용을 생략하고, 늘면 세부 내용을 보강하세요.";
        List<AiGeneratedTaskDto> generated = aiService.generateTasks(goal, constraint);
        List<ReplanTaskCandidate> candidates = generated.stream().map(item -> ReplanTaskCandidate.create(proposal.getId(),
                item.title(), item.category(), item.subject(), item.difficulty(), item.allocatedMinutes(),
                LearningTaskSource.AI_GENERATED)).toList();
        if (candidates.stream().mapToInt(ReplanTaskCandidate::getAllocatedMinutes).sum() > totalMinutes(goal, windows)) {
            throw new CustomException(LearningTaskErrorCode.PLAN_EXCEEDS_AVAILABLE_TIME);
        }
        candidateRepository.saveAll(candidates);
        return summary(proposal, windows, candidates.size());
    }

    public List<ReplanTaskCandidateResponseDto> getCandidates(Long memberId, Long goalId) {
        ReplanProposal proposal = getProposal(memberId, goalId);
        return candidateRepository.findByProposalIdOrderByIdAsc(proposal.getId()).stream()
                .map(ReplanTaskCandidateResponseDto::from).toList();
    }

    public CandidateAvailabilityStatusResponseDto getCapacity(Long memberId, Long goalId) {
        ReplanProposal proposal = getProposal(memberId, goalId);
        MemberGoal goal = getOwnedGoal(memberId, goalId);
        int candidateMinutes = candidateRepository.findByProposalIdOrderByIdAsc(proposal.getId()).stream()
                .mapToInt(ReplanTaskCandidate::getAllocatedMinutes).sum();
        int availabilityMinutes = totalMinutes(goal, replanAvailabilityRepository.findByProposalIdOrderByDayOfWeekAscStartTimeAsc(proposal.getId()));
        return new CandidateAvailabilityStatusResponseDto(candidateMinutes, availabilityMinutes,
                availabilityMinutes > 0 && candidateMinutes <= availabilityMinutes);
    }

    @Transactional
    public ReplanTaskCandidateResponseDto addCandidate(Long memberId, Long goalId, LearningTaskCandidateRequestDto request) {
        ReplanProposal proposal = getProposalForUpdate(memberId, goalId);
        ReplanTaskCandidate candidate = ReplanTaskCandidate.create(proposal.getId(), request.title(), request.category(),
                request.subject(), request.difficulty(), request.allocatedMinutes(), LearningTaskSource.USER_ADDED);
        return ReplanTaskCandidateResponseDto.from(candidateRepository.save(candidate));
    }

    @Transactional
    public ReplanTaskCandidateResponseDto updateCandidate(Long memberId, Long goalId, Long candidateId,
                                                           LearningTaskCandidateRequestDto request) {
        ReplanProposal proposal = getProposalForUpdate(memberId, goalId);
        ReplanTaskCandidate candidate = candidateRepository.findById(candidateId)
                .filter(item -> item.getProposalId().equals(proposal.getId()))
                .orElseThrow(() -> new CustomException(LearningTaskErrorCode.CANDIDATE_NOT_FOUND));
        candidate.update(request.title(), request.category(), request.subject(), request.difficulty(), request.allocatedMinutes());
        return ReplanTaskCandidateResponseDto.from(candidate);
    }

    @Transactional
    public void deleteCandidate(Long memberId, Long goalId, Long candidateId) {
        ReplanProposal proposal = getProposalForUpdate(memberId, goalId);
        ReplanTaskCandidate candidate = candidateRepository.findById(candidateId)
                .filter(item -> item.getProposalId().equals(proposal.getId()))
                .orElseThrow(() -> new CustomException(LearningTaskErrorCode.CANDIDATE_NOT_FOUND));
        candidateRepository.delete(candidate);
    }

    @Transactional
    public ScheduleResponseDto confirm(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoalForUpdate(memberId, goalId);
        ReplanProposal proposal = proposalRepository.findByGoalId(goalId)
                .orElseThrow(() -> new CustomException(GoalErrorCode.REPLAN_NOT_FOUND));
        List<ReplanAvailability> proposalWindows = replanAvailabilityRepository.findByProposalIdOrderByDayOfWeekAscStartTimeAsc(proposal.getId());
        List<ReplanTaskCandidate> candidates = candidateRepository.findByProposalIdOrderByIdAsc(proposal.getId());
        if (candidates.isEmpty() || candidates.stream().mapToInt(ReplanTaskCandidate::getAllocatedMinutes).sum() > totalMinutes(goal, proposalWindows)) {
            throw new CustomException(LearningTaskErrorCode.PLAN_EXCEEDS_AVAILABLE_TIME);
        }

        removeUnfinishedPlan(goalId);
        Member member = memberService.getActiveMember(memberId);
        availabilityRepository.deleteByMemberGoalId(goalId);
        availabilityRepository.saveAll(proposalWindows.stream().map(item -> Availability.create(member, goalId,
                item.getDayOfWeek(), item.getStartTime(), item.getEndTime(), item.isEnabled())).toList());
        List<LearningTask> newTasks = candidates.stream().map(item -> LearningTask.builder().goalId(goalId)
                .title(item.getTitle()).category(item.getCategory()).subject(item.getSubject()).difficulty(item.getDifficulty())
                .allocatedMinutes(item.getAllocatedMinutes()).source(item.getSource()).isModified(item.isModified()).build()).toList();
        taskRepository.saveAll(newTasks);
        deleteProposalData(proposal);
        return scheduleService.generateSchedule(memberId, goalId);
    }

    @Transactional
    public void cancel(Long memberId, Long goalId) {
        deleteProposalData(getProposalForUpdate(memberId, goalId));
    }

    public ReplanProposalSummaryResponseDto getSummary(Long memberId, Long goalId) {
        ReplanProposal proposal = getProposal(memberId, goalId);
        List<ReplanAvailability> windows = replanAvailabilityRepository.findByProposalIdOrderByDayOfWeekAscStartTimeAsc(proposal.getId());
        return summary(proposal, windows, candidateRepository.findByProposalIdOrderByIdAsc(proposal.getId()).size());
    }

    private void removeUnfinishedPlan(Long goalId) {
        for (Schedule schedule : scheduleRepository.findByGoalIdOrderByStartDateDesc(goalId)) {
            List<ScheduleItem> items = scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAscForUpdate(schedule.getId());
            List<ScheduleItem> unfinished = items.stream().filter(item -> item.getStatus() != ScheduleItemStatus.FINISHED).toList();
            scheduleItemRepository.deleteAll(unfinished);
            scheduleItemRepository.flush();
            if (items.stream().noneMatch(item -> item.getStatus() == ScheduleItemStatus.FINISHED)) scheduleRepository.delete(schedule);
        }
        for (LearningTask task : taskRepository.findByGoalIdOrderByIdAsc(goalId)) {
            boolean hasFinished = scheduleItemRepository.findByLearningTaskIdOrderByDateAscOrderIndexAsc(task.getId()).stream()
                    .anyMatch(item -> item.getStatus() == ScheduleItemStatus.FINISHED);
            if (hasFinished) task.updateStatus(LearningTaskStatus.FINISHED);
            else {
                scheduleFailureRepository.deleteByLearningTaskId(task.getId());
                taskRepository.delete(task);
            }
        }
    }

    private void deleteProposalData(ReplanProposal proposal) {
        candidateRepository.deleteByProposalId(proposal.getId());
        replanAvailabilityRepository.deleteByProposalId(proposal.getId());
        proposalRepository.delete(proposal);
    }

    private ReplanProposal getProposal(Long memberId, Long goalId) {
        getOwnedGoal(memberId, goalId);
        return proposalRepository.findByGoalId(goalId).orElseThrow(() -> new CustomException(GoalErrorCode.REPLAN_NOT_FOUND));
    }
    private ReplanProposal getProposalForUpdate(Long memberId, Long goalId) {
        getOwnedGoalForUpdate(memberId, goalId);
        return proposalRepository.findByGoalId(goalId).orElseThrow(() -> new CustomException(GoalErrorCode.REPLAN_NOT_FOUND));
    }
    private MemberGoal getOwnedGoal(Long memberId, Long goalId) {
        MemberGoal goal = goalRepository.findById(goalId).orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
        return goal;
    }
    private MemberGoal getOwnedGoalForUpdate(Long memberId, Long goalId) {
        MemberGoal goal = goalRepository.findByIdForUpdate(goalId).orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
        return goal;
    }
    private void validateAvailabilityRequests(AvailabilityReplaceRequest request) {
        List<com.steadyteller.backend.member.dto.AvailabilityRequest> windows = request.availabilities();
        for (int i = 0; i < windows.size(); i++) {
            var item = windows.get(i);
            if (!item.getEndTime().isAfter(item.getStartTime())) throw new CustomException(GlobalErrorCode.INVALID_INPUT_VALUE);
            for (int j = i + 1; j < windows.size(); j++) {
                var other = windows.get(j);
                if (item.getDayOfWeek() == other.getDayOfWeek()
                        && item.getStartTime().isBefore(other.getEndTime())
                        && item.getEndTime().isAfter(other.getStartTime())) {
                    throw new CustomException(GlobalErrorCode.INVALID_INPUT_VALUE);
                }
            }
        }
    }
    private int totalMinutes(MemberGoal goal, List<ReplanAvailability> windows) {
        LocalDate date = goal.getStartDate().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))) ? goal.getStartDate() : LocalDate.now(ZoneId.of("Asia/Seoul"));
        int total = 0;
        while (!date.isAfter(goal.getTargetDate())) {
            LocalDate current = date;
            total += windows.stream().filter(item -> item.isEnabled() && item.getDayOfWeek() == current.getDayOfWeek())
                    .mapToInt(ReplanAvailability::getAvailableMinutes).sum();
            date = date.plusDays(1);
        }
        return total;
    }
    private int remainingMinutes(LearningTask task) {
        List<ScheduleItem> items = scheduleItemRepository.findByLearningTaskIdOrderByDateAscOrderIndexAsc(task.getId());
        if (items.isEmpty()) return task.getStatus() == LearningTaskStatus.FINISHED ? 0 : task.getAllocatedMinutes();
        return items.stream().filter(item -> item.getStatus() != ScheduleItemStatus.FINISHED)
                .mapToInt(ScheduleItem::getAllocatedMinutes).sum();
    }
    private ReplanProposalSummaryResponseDto summary(ReplanProposal proposal, List<ReplanAvailability> windows, int count) {
        List<AvailabilityResponse> responses = windows.stream().map(item -> AvailabilityResponse.builder().id(item.getId())
                .dayOfWeek(item.getDayOfWeek()).startTime(item.getStartTime()).endTime(item.getEndTime())
                .availableMinutes(item.getAvailableMinutes()).enabled(item.isEnabled()).build()).toList();
        return new ReplanProposalSummaryResponseDto(proposal.getId(), responses, count, proposal.getCreatedAt());
    }
}
