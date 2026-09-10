package com.steadyteller.backend.learningtask.event;

import com.steadyteller.backend.learningtask.repository.LearningTaskCandidateRepository;
import com.steadyteller.backend.learningtask.repository.LearningTaskRepository;
import com.steadyteller.backend.membergoal.event.MemberGoalDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * MemberGoal 삭제 이벤트를 수신하여 해당 목표에 속한 후보 태스크와 확정 태스크를 연쇄 정리한다.
 */
@Component
@RequiredArgsConstructor
public class LearningTaskGoalEventListener {

    private final LearningTaskCandidateRepository candidateRepository;
    private final LearningTaskRepository learningTaskRepository;

    @EventListener
    @Transactional
    public void handleGoalDeleted(MemberGoalDeletedEvent event) {
        candidateRepository.deleteByGoalId(event.goalId());
        learningTaskRepository.deleteByGoalId(event.goalId());
    }
}
