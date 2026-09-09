package com.steadyteller.backend.learningtask.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.candidate.LearningTaskCandidate;
import com.steadyteller.backend.learningtask.candidate.LearningTaskCandidateStore;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.learningtask.entity.LearningTaskStatus;
import com.steadyteller.backend.learningtask.repository.LearningTaskRepository;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningTaskServiceTest {

    @Mock
    private MemberGoalRepository memberGoalRepository;
    @Mock
    private LearningTaskRepository learningTaskRepository;
    @Mock
    private LearningTaskAiService learningTaskAiService;
    @Mock
    private LearningTaskCandidateStore candidateStore;

    @InjectMocks
    private LearningTaskService learningTaskService;

    @Test
    void confirmTasksRejectsWhenThereAreNoCandidates() {
        // 후보를 하나도 만들지 않은 상태에서는 빈 태스크 목록을 확정할 수 없다.
        MemberGoal goal = MemberGoal.builder()
                .memberId(1L).title("Java").startDate(LocalDate.of(2026, 2, 1))
                .targetDate(LocalDate.of(2026, 2, 2)).currentLevel("BEGINNER")
                .dailyStudyHours(1).availableDays(List.of("MON")).focusArea("backend").build();
        given(memberGoalRepository.findById(10L)).willReturn(Optional.of(goal));
        given(candidateStore.findByGoal(10L)).willReturn(List.of());

        assertThatThrownBy(() -> learningTaskService.confirmTasks(1L, 10L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void confirmTasksReplacesExistingPendingTasks() {
        MemberGoal goal = MemberGoal.builder()
                .memberId(1L).title("Java").startDate(LocalDate.now())
                .targetDate(LocalDate.now().plusDays(1)).currentLevel("BEGINNER")
                .dailyStudyHours(1).availableDays(List.of("MON")).focusArea("backend").build();
        LearningTaskCandidate candidate = new LearningTaskCandidate(
                1L, 10L, 1L, "Spring", "backend", "Spring MVC", 3, 30,
                LearningTaskSource.AI_GENERATED, false);
        LearningTask existingTask = org.mockito.Mockito.mock(LearningTask.class);

        given(memberGoalRepository.findById(10L)).willReturn(Optional.of(goal));
        given(candidateStore.findByGoal(10L)).willReturn(List.of(candidate));
        given(learningTaskRepository.findByGoalIdAndStatus(10L, LearningTaskStatus.PENDING))
                .willReturn(List.of(existingTask));

        learningTaskService.confirmTasks(1L, 10L);

        verify(learningTaskRepository).deleteAll(List.of(existingTask));
        verify(learningTaskRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
