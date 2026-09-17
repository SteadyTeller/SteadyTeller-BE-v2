package com.steadyteller.backend.membergoal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.port.GoalRemainingTaskQueryPort;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalDeadlineServiceTest {
    @Mock private MemberGoalRepository repository;
    @Mock private GoalRemainingTaskQueryPort queryPort;
    @InjectMocks private GoalDeadlineService service;

    @Test
    void rejectsAnotherMembersGoalBeforeReadingTasks() {
        given(repository.findById(10L)).willReturn(Optional.of(goal(today().minusDays(1))));
        assertThatThrownBy(() -> service.getDeadline(2L, 10L)).isInstanceOf(CustomException.class);
        verifyNoInteractions(queryPort);
    }

    @Test
    void targetDateRemainsAvailableThroughTheEndOfTheDay() {
        given(repository.findById(10L)).willReturn(Optional.of(goal(today())));
        given(queryPort.findRemainingTasks(10L)).willReturn(List.of());
        assertThat(service.getDeadline(1L, 10L).deadlineReached()).isFalse();
    }

    @Test
    void yesterdayIsReachedWithoutChangingOriginalTarget() {
        LocalDate date = today().minusDays(1);
        MemberGoal goal = goal(date);
        given(repository.findById(10L)).willReturn(Optional.of(goal));
        given(queryPort.findRemainingTasks(10L)).willReturn(List.of());
        assertThat(service.getDeadline(1L, 10L).deadlineReached()).isTrue();
        assertThat(goal.getTargetDate()).isEqualTo(date);
    }

    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Seoul")); }
    private MemberGoal goal(LocalDate date) {
        return MemberGoal.builder().memberId(1L).title("Java").startDate(date.minusDays(1))
                .targetDate(date).currentLevel("BEGINNER").dailyStudyHours(1)
                .availableDays(List.of("MON")).focusArea("Java").build();
    }
}
