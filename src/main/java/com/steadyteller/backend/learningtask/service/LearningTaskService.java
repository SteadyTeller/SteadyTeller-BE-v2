package com.steadyteller.backend.learningtask.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.exception.GoalErrorCode;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.learningtask.candidate.LearningTaskCandidate;
import com.steadyteller.backend.learningtask.dto.AiGeneratedTaskDto;
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
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.member.domain.Availability;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class LearningTaskService {

    private final MemberGoalRepository memberGoalRepository;
    private final LearningTaskRepository learningTaskRepository;
    private final LearningTaskCandidateRepository candidateRepository;
    private final LearningTaskAiService learningTaskAiService;
    private final ScheduleItemRepository scheduleItemRepository;
    private final com.steadyteller.backend.schedule.repository.ScheduleFailureRepository scheduleFailureRepository;

    /**
     * AI 세부 태스크 생성 (설계 명세 2번). 기존에 남아있던 해당 goal의 후보는 새 결과로 교체된다.
     */
    @Transactional
    public List<LearningTaskCandidateResponseDto> generateTasks(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoalForUpdate(memberId, goalId);
        List<AiGeneratedTaskDto> aiResults = learningTaskAiService.generateTasks(goal);

        candidateRepository.deleteByGoalId(goalId);
        List<LearningTaskCandidate> newCandidates = aiResults.stream()
                .map(result -> LearningTaskCandidate.builder()
                        .goalId(goalId)
                        .memberId(memberId)
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

    /** Generates candidates with the actual weekly windows and rejects plans that cannot fit by the deadline. */
    @Transactional
    public List<LearningTaskCandidateResponseDto> generateTasksForAvailability(
            Long memberId, Long goalId, List<Availability> availabilities
    ) {
        MemberGoal goal = getOwnedGoalForUpdate(memberId, goalId);
        int totalMinutes = totalAvailabilityMinutes(goal, availabilities);
        int largestWindowMinutes = availabilities.stream().filter(Availability::isEnabled)
                .mapToInt(Availability::getAvailableMinutes).max().orElse(0);
        if (totalMinutes <= 0 || largestWindowMinutes <= 0) {
            throw new CustomException(LearningTaskErrorCode.PLAN_EXCEEDS_AVAILABLE_TIME);
        }
        String constraint = "Total until deadline: %d minutes; maximum single task: %d minutes; windows: %s"
                .formatted(totalMinutes, largestWindowMinutes, summarizeAvailability(availabilities));
        List<AiGeneratedTaskDto> aiResults;
        try {
            aiResults = learningTaskAiService.generateTasks(goal, constraint);
        } catch (CustomException exception) {
            if (exception.getErrorCode() != LearningTaskErrorCode.AI_GENERATION_FAILED) throw exception;
            log.warn("AI task generation failed; using availability-safe fallback tasks. goalId={}", goalId);
            aiResults = fallbackTasks(goal, totalMinutes, largestWindowMinutes);
        }
        int generatedMinutes = aiResults.stream().mapToInt(AiGeneratedTaskDto::allocatedMinutes).sum();
        if (generatedMinutes > totalMinutes || aiResults.stream()
                .anyMatch(task -> task.allocatedMinutes() > largestWindowMinutes)) {
            throw new CustomException(LearningTaskErrorCode.PLAN_EXCEEDS_AVAILABLE_TIME);
        }
        candidateRepository.deleteByGoalId(goalId);
        List<LearningTaskCandidate> candidates = aiResults.stream().map(result -> LearningTaskCandidate.builder()
                .goalId(goalId).memberId(memberId).title(result.title()).category(result.category())
                .subject(result.subject()).difficulty(result.difficulty()).allocatedMinutes(result.allocatedMinutes())
                .source(LearningTaskSource.AI_GENERATED).modified(false).build()).toList();
        return candidateRepository.saveAll(candidates).stream().map(LearningTaskCandidateResponseDto::from).toList();
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

    /** Keeps the user moving when the external AI provider is unavailable. */
    private List<AiGeneratedTaskDto> fallbackTasks(MemberGoal goal, int totalMinutes, int largestWindowMinutes) {
        int taskMinutes = Math.min(Math.min(60, largestWindowMinutes), totalMinutes);
        int count = Math.max(1, Math.min(4, totalMinutes / taskMinutes));
        List<String> steps = List.of("핵심 개념 정리", "기본 예제 학습", "실습 및 적용", "복습과 체크");
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new AiGeneratedTaskDto(
                        goal.getTitle() + " · " + steps.get(index),
                        goal.getMustStudyTopics().getFirst(), goal.getTitle(), Math.min(3 + index / 2, 5), taskMinutes))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LearningTaskCandidateResponseDto> getCandidates(Long memberId, Long goalId) {
        getOwnedGoal(memberId, goalId);
        return candidateRepository.findByGoalIdOrderByIdAsc(goalId).stream()
                .map(LearningTaskCandidateResponseDto::from)
                .toList();
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
        getOwnedGoalForUpdate(memberId, goalId);

        LearningTaskCandidate candidate = LearningTaskCandidate.builder()
                .goalId(goalId)
                .memberId(memberId)
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
        candidate.update(request.title(), request.category(), request.subject(),
                request.difficulty(), request.allocatedMinutes());
        return LearningTaskCandidateResponseDto.from(candidate);
    }

    @Transactional
    public void deleteCandidate(Long memberId, Long candidateId) {
        LearningTaskCandidate candidate = getOwnedCandidate(memberId, candidateId);
        candidateRepository.delete(candidate);
    }

    /** Deletes a confirmed task but preserves its booked calendar slots as empty reservations. */
    @Transactional
    public void deleteConfirmedTask(Long memberId, Long taskId) {
        LearningTask task = learningTaskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(LearningTaskErrorCode.CANDIDATE_NOT_FOUND));
        getOwnedGoalForUpdate(memberId, task.getGoalId());
        List<ScheduleItem> scheduledItems = scheduleItemRepository.findByLearningTaskIdForUpdate(taskId);
        scheduledItems.forEach(item -> {
            if (item.getStatus() == com.steadyteller.backend.schedule.entity.ScheduleItemStatus.PENDING) {
                item.clearTask();
            }
        });
        scheduleFailureRepository.deleteByLearningTaskId(taskId);
        learningTaskRepository.delete(task);
    }

    /**
     * 최종 승인. 이 시점의 후보 목록을 LearningTask로 일괄 저장한다 (status=PENDING, reviewedAt=저장 시각).
     */
    @Transactional
    public List<LearningTaskResponseDto> confirmTasks(Long memberId, Long goalId) {
        getOwnedGoalForUpdate(memberId, goalId);

        List<LearningTaskCandidate> candidates = candidateRepository.findByGoalIdOrderByIdAsc(goalId);
        if (candidates.isEmpty()) {
            throw new CustomException(LearningTaskErrorCode.CANDIDATE_NOT_FOUND);
        }
        List<LearningTask> tasks = candidates.stream()
                .map(candidate -> LearningTask.builder()
                        .goalId(goalId)
                        .title(candidate.getTitle())
                        .category(candidate.getCategory())
                        .subject(candidate.getSubject())
                        .importance(candidate.getImportance())
                        .difficulty(candidate.getDifficulty())
                        .allocatedMinutes(candidate.getAllocatedMinutes())
                        .source(candidate.getSource())
                        .isModified(candidate.isModified())
                        .build())
                .toList();

        List<LearningTask> existingTasks = learningTaskRepository.findByGoalIdAndStatusIn(
                goalId, List.of(LearningTaskStatus.PENDING, LearningTaskStatus.SCHEDULED));
        
        // 스케줄에 이미 배정된(SCHEDULED) 태스크가 삭제될 경우 고아 참조를 방지하기 위해 빈 일정으로 처리한다.
        for (LearningTask task : existingTasks) {
            if (task.getStatus() == LearningTaskStatus.SCHEDULED) {
                List<ScheduleItem> scheduledItems = scheduleItemRepository.findByLearningTaskIdForUpdate(task.getId());
                scheduledItems.forEach(item -> {
                    if (item.getStatus() == com.steadyteller.backend.schedule.entity.ScheduleItemStatus.PENDING) {
                        item.clearTask();
                    }
                });
            }
        }
        
        java.util.List<Long> taskIds = existingTasks.stream().map(com.steadyteller.backend.learningtask.entity.LearningTask::getId).toList();
        scheduleFailureRepository.deleteByLearningTaskIdIn(taskIds);
        learningTaskRepository.deleteAll(existingTasks);
        List<LearningTask> saved = learningTaskRepository.saveAll(tasks);
        candidateRepository.deleteByGoalId(goalId);

        return saved.stream().map(LearningTaskResponseDto::from).toList();
    }

    private MemberGoal getOwnedGoal(Long memberId, Long goalId) {
        MemberGoal goal = memberGoalRepository.findById(goalId)
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) {
            throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
        }
        return goal;
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
        if (!candidate.getMemberId().equals(memberId)) {
            throw new CustomException(LearningTaskErrorCode.CANDIDATE_ACCESS_DENIED);
        }
        return candidate;
    }
}
