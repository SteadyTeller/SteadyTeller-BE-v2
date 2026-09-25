package com.steadyteller.backend.learningtask.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.exception.GoalErrorCode;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.learningtask.candidate.LearningTaskCandidate;
import com.steadyteller.backend.learningtask.dto.AiGeneratedTaskDto;
import com.steadyteller.backend.learningtask.dto.CandidateAvailabilityStatusResponseDto;
import com.steadyteller.backend.learningtask.dto.LearningTaskCandidateRequestDto;
import com.steadyteller.backend.learningtask.dto.LearningTaskCandidateResponseDto;
import com.steadyteller.backend.learningtask.dto.LearningTaskResponseDto;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.learningtask.entity.LearningTaskStatus;
import com.steadyteller.backend.learningtask.exception.LearningTaskErrorCode;
import com.steadyteller.backend.learningtask.repository.LearningTaskCandidateRepository;
import com.steadyteller.backend.learningtask.repository.LearningTaskRepository;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.entity.ScheduleItemStatus;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.service.ScheduleService;
import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;
import com.steadyteller.backend.member.domain.Availability;
import com.steadyteller.backend.member.repository.AvailabilityRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습 항목 검토 단계 (설계 명세 3번).
 * 승인 전까지는 learning_task_candidate 테이블만 다루고, confirm 시점에 LearningTask로 일괄 저장한다.
 * <p>
 * generate/add/confirm은 같은 goal의 후보 집합을 통째로 읽고 바꾸는 복합 연산이라, 서로 경합하면
 * 후보 유실이나 중복 확정이 생길 수 있다. 그래서 이 세 메서드는 항상 {@link #getOwnedGoalForUpdate}로
 * 해당 MemberGoal 로우에 비관적 락을 먼저 잡고 트랜잭션이 끝날 때까지 유지해, goalId 단위로 서로 직렬화되게 한다.
 */
@Service
@RequiredArgsConstructor
public class LearningTaskService {

    private final MemberGoalRepository memberGoalRepository;
    private final LearningTaskRepository learningTaskRepository;
    private final LearningTaskCandidateRepository candidateRepository;
    private final LearningTaskAiService learningTaskAiService;
    private final ScheduleItemRepository scheduleItemRepository;
    private final com.steadyteller.backend.schedule.repository.ScheduleFailureRepository scheduleFailureRepository;
    private final AvailabilityRepository availabilityRepository;
    private final ScheduleService scheduleService;

    /**
     * AI 세부 태스크 생성 (설계 명세 2번). 기존에 남아있던 해당 goal의 후보는 새 결과로 교체된다.
     */
    @Transactional
    public List<LearningTaskCandidateResponseDto> generateTasks(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoalForUpdate(memberId, goalId);
        assertTaskGenerationAllowed(goal);
        List<Availability> availabilities = availabilityRepository
                .findAllByMemberGoalIdOrderByDayOfWeekAscStartTimeAsc(goalId);
        int totalAvailableMinutes = totalAvailabilityMinutes(goal, availabilities);
        if (totalAvailableMinutes <= 0) {
            throw new CustomException(LearningTaskErrorCode.PLAN_EXCEEDS_AVAILABLE_TIME);
        }
        String constraint = "목표일 전까지 총 가용 학습 시간: %d분; 시간대: %s"
                .formatted(totalAvailableMinutes, summarizeAvailability(availabilities));
        List<AiGeneratedTaskDto> aiResults = learningTaskAiService.generateTasks(goal, constraint);
        int generatedMinutes = aiResults.stream().mapToInt(AiGeneratedTaskDto::allocatedMinutes).sum();
        if (generatedMinutes > totalAvailableMinutes) {
            throw new CustomException(LearningTaskErrorCode.PLAN_EXCEEDS_AVAILABLE_TIME);
        }

        candidateRepository.deleteByGoalId(goalId);
        List<LearningTaskCandidate> newCandidates = aiResults.stream()
                .map(result -> LearningTaskCandidate.builder()
                        .goalId(goalId)
                        .title(result.title())
                        .category(result.category())
                        .subject(result.subject())
                        .difficulty(result.difficulty())
                        .allocatedMinutes(result.allocatedMinutes())
                        .source(LearningTaskSource.AI_GENERATED)
                        .modified(false)
                        .build())
                .toList();

        List<LearningTaskCandidate> saved = candidateRepository.saveAll(newCandidates);
        return saved.stream().map(LearningTaskCandidateResponseDto::from).toList();
    }

    private int totalAvailabilityMinutes(MemberGoal goal, List<Availability> availabilities) {
        Map<DayOfWeek, Integer> minutesByDay = availabilities.stream().filter(Availability::isEnabled)
                .collect(java.util.stream.Collectors.groupingBy(Availability::getDayOfWeek,
                        java.util.stream.Collectors.summingInt(Availability::getAvailableMinutes)));
        LocalDate cursor = goal.getStartDate().isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))) ? goal.getStartDate() : LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        int total = 0;
        while (!cursor.isAfter(goal.getTargetDate())) {
            total += minutesByDay.getOrDefault(cursor.getDayOfWeek(), 0);
            cursor = cursor.plusDays(1);
        }
        return total;
    }

    private String summarizeAvailability(List<Availability> availabilities) {
        return availabilities.stream().filter(Availability::isEnabled)
                .map(item -> item.getDayOfWeek() + " " + item.getStartTime() + "-" + item.getEndTime())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    @Transactional(readOnly = true)
    public List<LearningTaskCandidateResponseDto> getCandidates(Long memberId, Long goalId) {
        getOwnedGoal(memberId, goalId);
        return candidateRepository.findByGoalIdOrderByIdAsc(goalId).stream()
                .map(LearningTaskCandidateResponseDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CandidateAvailabilityStatusResponseDto getCandidateAvailabilityStatus(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoal(memberId, goalId);
        return candidateAvailabilityStatus(goal, goalId);
    }

    /**
     * 확정된 태스크 목록을 조회한다. 확정 후에는 후보 목록이 삭제되므로, 화면에서
     * 확정 결과를 다시 표시할 때는 후보 조회 API 대신 이 메서드를 사용해야 한다.
     */
    @Transactional(readOnly = true)
    public List<LearningTaskResponseDto> getConfirmedTasks(Long memberId, Long goalId) {
        getOwnedGoal(memberId, goalId);
        return learningTaskRepository.findByGoalIdOrderByIdAsc(goalId).stream()
                .map(task -> LearningTaskResponseDto.from(task,
                        scheduleItemRepository.findByLearningTaskIdOrderByDateAscOrderIndexAsc(task.getId()).stream()
                                .map(com.steadyteller.backend.learningtask.dto.TaskSchedulePlacementResponseDto::from).toList()))
                .toList();
    }

    @Transactional
    public LearningTaskCandidateResponseDto addUserCandidate(Long memberId, Long goalId,
                                                               LearningTaskCandidateRequestDto request) {
        MemberGoal goal = getOwnedGoalForUpdate(memberId, goalId);
        assertTaskGenerationAllowed(goal);

        LearningTaskCandidate candidate = LearningTaskCandidate.builder()
                .goalId(goalId)
                .title(request.title())
                .category(request.category())
                .subject(request.subject())
                .difficulty(request.difficulty())
                .allocatedMinutes(request.allocatedMinutes())
                .source(LearningTaskSource.USER_ADDED)
                .modified(false)
                .build();

        return LearningTaskCandidateResponseDto.from(candidateRepository.save(candidate));
    }

    @Transactional
    public LearningTaskCandidateResponseDto updateCandidate(Long memberId, Long candidateId,
                                                              LearningTaskCandidateRequestDto request) {
        LearningTaskCandidate candidate = getOwnedCandidate(memberId, candidateId);
        assertTaskGenerationAllowed(getOwnedGoal(memberId, candidate.getGoalId()));
        candidate.update(request.title(), request.category(), request.subject(),
                request.difficulty(), request.allocatedMinutes());
        return LearningTaskCandidateResponseDto.from(candidate);
    }

    @Transactional
    public void deleteCandidate(Long memberId, Long candidateId) {
        LearningTaskCandidate candidate = getOwnedCandidate(memberId, candidateId);
        assertTaskGenerationAllowed(getOwnedGoal(memberId, candidate.getGoalId()));
        candidateRepository.delete(candidate);
    }

    /** Deletes an unfinished confirmed task and its unfinished schedule placements. */
    @Transactional
    public void deleteConfirmedTask(Long memberId, Long taskId) {
        LearningTask task = learningTaskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(LearningTaskErrorCode.CONFIRMED_TASK_NOT_FOUND));
        getOwnedGoalForUpdate(memberId, task.getGoalId());
        List<ScheduleItem> scheduledItems = scheduleItemRepository.findByLearningTaskIdForUpdate(taskId);
        if (task.getStatus() == LearningTaskStatus.FINISHED
                || scheduledItems.stream().anyMatch(item -> item.getStatus() == ScheduleItemStatus.FINISHED)) {
            throw new CustomException(LearningTaskErrorCode.COMPLETED_TASK_CANNOT_BE_DELETED);
        }
        scheduleFailureRepository.deleteByLearningTaskId(taskId);
        scheduleItemRepository.deleteAll(scheduledItems);
        learningTaskRepository.delete(task);
    }

    /**
     * 최종 승인. 이 시점의 후보 목록을 LearningTask로 일괄 저장한다 (status=PENDING).
     */
    @Transactional
    public ScheduleResponseDto confirmTasksAndGenerateSchedule(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoalForUpdate(memberId, goalId);
        assertTaskGenerationAllowed(goal);

        List<LearningTaskCandidate> candidates = candidateRepository.findByGoalIdOrderByIdAsc(goalId);
        if (candidates.isEmpty()) {
            throw new CustomException(LearningTaskErrorCode.CANDIDATE_NOT_FOUND);
        }
        if (!candidateAvailabilityStatus(goal, goalId).isWithinAvailability()) {
            throw new CustomException(LearningTaskErrorCode.PLAN_EXCEEDS_AVAILABLE_TIME);
        }
        List<LearningTask> tasks = candidates.stream()
                .map(candidate -> LearningTask.builder()
                        .goalId(goalId)
                        .title(candidate.getTitle())
                        .category(candidate.getCategory())
                        .subject(candidate.getSubject())
                        .difficulty(candidate.getDifficulty())
                        .allocatedMinutes(candidate.getAllocatedMinutes())
                        .source(candidate.getSource())
                        .isModified(candidate.isModified())
                        .build())
                .toList();

        List<LearningTask> saved = learningTaskRepository.saveAll(tasks);
        candidateRepository.deleteByGoalId(goalId);
        goal.lockTaskGeneration();

        return scheduleService.generateSchedule(memberId, goalId);
    }

    private MemberGoal getOwnedGoal(Long memberId, Long goalId) {
        MemberGoal goal = memberGoalRepository.findById(goalId)
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) {
            throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
        }
        return goal;
    }

    private CandidateAvailabilityStatusResponseDto candidateAvailabilityStatus(MemberGoal goal, Long goalId) {
        int totalAvailableMinutes = totalAvailabilityMinutes(goal,
                availabilityRepository.findAllByMemberGoalIdOrderByDayOfWeekAscStartTimeAsc(goalId));
        int candidateMinutes = candidateRepository.findByGoalIdOrderByIdAsc(goalId).stream()
                .mapToInt(LearningTaskCandidate::getAllocatedMinutes).sum();
        return new CandidateAvailabilityStatusResponseDto(candidateMinutes, totalAvailableMinutes,
                totalAvailableMinutes > 0 && candidateMinutes <= totalAvailableMinutes);
    }

    private void assertTaskGenerationAllowed(MemberGoal goal) {
        if (goal.isTaskGenerationLocked()) {
            throw new CustomException(LearningTaskErrorCode.TASK_GENERATION_LOCKED);
        }
    }

    /**
     * goal 로우에 비관적 락을 잡은 채로 소유권을 검증한다. generate/add/confirm처럼 해당 goal의 후보 집합을
     * 통째로 읽고 바꾸는 트랜잭션에서만 사용해, 같은 goalId에 대한 동시 요청을 직렬화한다.
     */
    private MemberGoal getOwnedGoalForUpdate(Long memberId, Long goalId) {
        MemberGoal goal = memberGoalRepository.findByIdForUpdate(goalId)
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) {
            throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
        }
        return goal;
    }

    private LearningTaskCandidate getOwnedCandidate(Long memberId, Long candidateId) {
        LearningTaskCandidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new CustomException(LearningTaskErrorCode.CANDIDATE_NOT_FOUND));
        getOwnedGoal(memberId, candidate.getGoalId());
        return candidate;
    }
}
