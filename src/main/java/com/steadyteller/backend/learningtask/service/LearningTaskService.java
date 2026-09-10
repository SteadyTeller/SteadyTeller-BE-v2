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

    @Transactional(readOnly = true)
    public List<LearningTaskCandidateResponseDto> getCandidates(Long memberId, Long goalId) {
        getOwnedGoal(memberId, goalId);
        return candidateRepository.findByGoalIdOrderByIdAsc(goalId).stream()
                .map(LearningTaskCandidateResponseDto::from)
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

        List<LearningTask> existingTasks = learningTaskRepository.findByGoalIdAndStatus(
                goalId, LearningTaskStatus.PENDING);
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
