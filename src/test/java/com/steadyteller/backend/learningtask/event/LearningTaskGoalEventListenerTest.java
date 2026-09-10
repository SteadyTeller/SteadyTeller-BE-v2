package com.steadyteller.backend.learningtask.event;

import static org.mockito.Mockito.verify;

import com.steadyteller.backend.learningtask.repository.LearningTaskCandidateRepository;
import com.steadyteller.backend.learningtask.repository.LearningTaskRepository;
import com.steadyteller.backend.membergoal.event.MemberGoalDeletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningTaskGoalEventListenerTest {

    @Mock
    private LearningTaskCandidateRepository candidateRepository;

    @Mock
    private LearningTaskRepository learningTaskRepository;

    @InjectMocks
    private LearningTaskGoalEventListener listener;

    @Test
    void deletesCandidatesAndTasksWhenGoalDeletedEventReceived() {
        Long goalId = 42L;
        MemberGoalDeletedEvent event = new MemberGoalDeletedEvent(goalId);

        listener.handleGoalDeleted(event);

        verify(candidateRepository).deleteByGoalId(goalId);
        verify(learningTaskRepository).deleteByGoalId(goalId);
    }
}
