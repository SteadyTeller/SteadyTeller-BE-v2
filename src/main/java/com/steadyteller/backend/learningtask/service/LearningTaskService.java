package com.steadyteller.backend.learningtask.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.exception.GoalErrorCode;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.learningtask.candidate.LearningTaskCandidate;
import com.steadyteller.backend.learningtask.candidate.LearningTaskCandidateStore;
import com.steadyteller.backend.learningtask.dto.AiGeneratedTaskDto;
import com.steadyteller.backend.learningtask.dto.LearningTaskCandidateRequestDto;
import com.steadyteller.backend.learningtask.dto.LearningTaskCandidateResponseDto;
import com.steadyteller.backend.learningtask.dto.LearningTaskResponseDto;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.learningtask.exception.LearningTaskErrorCode;
import com.steadyteller.backend.learningtask.repository.LearningTaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습 항목 검토 단계 (설계 명세 3번).
 * 승인 전까지는 LearningTaskCandidateStore(메모리 캐시)만 다루고, confirm 시점에 LearningTask로 일괄 저장한다.
 */
@Service
@RequiredArgsConstructor
public class LearningTaskService {

    private final MemberGoalRepository memberGoalRepository;
    private final LearningTaskRepository learningTaskRepository;
    private final LearningTaskCandidateStore candidateStore;
    private final LearningTaskAiService learningTaskAiService;

    /**
     * AI 세부 태스크 생성 (설계 명세 2번). 기존에 남아있던 해당 goal의 후보는 새 결과로 교체된다.
     */
    public List<LearningTaskCandidateResponseDto> generateTasks(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoal(memberId, goalId);
        List<AiGeneratedTaskDto> aiResults = learningTaskAiService.generateTasks(goal);

        List<LearningTaskCandidate> newCandidates = aiResults.stream()
                .map(result -> new LearningTaskCandidate(
                        candidateStore.nextId(),
                        goalId,
                        memberId,
                        result.title(),
                        result.category(),
                        result.subject(),
                        result.difficulty(),
                        result.allocatedMinutes(),
                        LearningTaskSource.AI_GENERATED,
                        false
                ))
                .toList();

        candidateStore.replaceForGoal(goalId, newCandidates);
        return newCandidates.stream().map(LearningTaskCandidateResponseDto::from).toList();
    }

    public List<LearningTaskCandidateResponseDto> getCandidates(Long memberId, Long goalId) {
        getOwnedGoal(memberId, goalId);
        return candidateStore.findByGoal(goalId).stream()
                .map(LearningTaskCandidateResponseDto::from)
                .toList();
    }

    public LearningTaskCandidateResponseDto addUserCandidate(Long memberId, Long goalId,
                                                               LearningTaskCandidateRequestDto request) {
        getOwnedGoal(memberId, goalId);

        LearningTaskCandidate candidate = new LearningTaskCandidate(
                candidateStore.nextId(),
                goalId,
                memberId,
                request.title(),
                request.category(),
                request.subject(),
                request.difficulty(),
                request.allocatedMinutes(),
                LearningTaskSource.USER_ADDED,
                false
        );

        candidateStore.add(candidate);
        return LearningTaskCandidateResponseDto.from(candidate);
    }

    public LearningTaskCandidateResponseDto updateCandidate(Long memberId, Long candidateId,
                                                              LearningTaskCandidateRequestDto request) {
        LearningTaskCandidate candidate = getOwnedCandidate(memberId, candidateId);
        candidate.update(request.title(), request.category(), request.subject(),
                request.difficulty(), request.allocatedMinutes());
        return LearningTaskCandidateResponseDto.from(candidate);
    }

    public void deleteCandidate(Long memberId, Long candidateId) {
        LearningTaskCandidate candidate = getOwnedCandidate(memberId, candidateId);
        candidateStore.remove(candidate.getCandidateId());
    }

    /**
     * 최종 승인. 이 시점의 후보 목록을 LearningTask로 일괄 저장한다 (status=PENDING, reviewedAt=저장 시각).
     */
    @Transactional
    public List<LearningTaskResponseDto> confirmTasks(Long memberId, Long goalId) {
        getOwnedGoal(memberId, goalId);

        List<LearningTaskCandidate> candidates = candidateStore.findByGoal(goalId);
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

        List<LearningTask> saved = learningTaskRepository.saveAll(tasks);
        candidateStore.clearForGoal(goalId);

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

    private LearningTaskCandidate getOwnedCandidate(Long memberId, Long candidateId) {
        LearningTaskCandidate candidate = candidateStore.get(candidateId);
        if (!candidate.getMemberId().equals(memberId)) {
            throw new CustomException(LearningTaskErrorCode.CANDIDATE_ACCESS_DENIED);
        }
        return candidate;
    }
}
