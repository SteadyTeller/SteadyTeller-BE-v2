package com.steadyteller.backend.membergoal.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.member.domain.LearningLevel;
import com.steadyteller.backend.membergoal.dto.MemberStudyInfoRequestDto;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.steadyteller.backend.membergoal.event.MemberGoalDeletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberGoalServiceTest {

    @Mock
    private MemberGoalRepository memberGoalRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MemberGoalService memberGoalService;

    @Test
    void createGoalRejectsReversedDates() {
        // 목표 시작일이 목표일보다 늦으면 목표를 생성할 수 없다.
        MemberStudyInfoRequestDto request = request(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 1), List.of("MON"));

        assertThatThrownBy(() -> memberGoalService.createGoal(1L, request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void createGoalRejectsTargetDateBeforeToday() {
        MemberStudyInfoRequestDto request = request(
                LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), List.of("MON"));

        assertThatThrownBy(() -> memberGoalService.createGoal(1L, request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void createGoalRejectsInvalidOrDuplicateAvailableDays() {
        // 같은 요일이 중복으로 전달되면 유효하지 않은 목표 설정으로 처리한다.
        MemberStudyInfoRequestDto request = request(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2), List.of("MON", "MON"));

        assertThatThrownBy(() -> memberGoalService.createGoal(1L, request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void createGoalRejectsUnknownAvailableDay() {
        // 명세에 정의되지 않은 요일 표현은 허용하지 않는다.
        MemberStudyInfoRequestDto request = request(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2), List.of("Monday"));

        assertThatThrownBy(() -> memberGoalService.createGoal(1L, request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void deleteGoalPublishesMemberGoalDeletedEventAndDeletesEntity() {
        Long memberId = 1L;
        Long goalId = 10L;
        MemberGoal goal = MemberGoal.builder()
                .memberId(memberId).title("Java").startDate(LocalDate.of(2026, 2, 1))
                .targetDate(LocalDate.of(2026, 2, 2)).currentLevel("BEGINNER")
                .dailyStudyHours(1).availableDays(List.of("MON")).focusArea("backend").build();
        given(memberGoalRepository.findByIdForUpdate(goalId)).willReturn(Optional.of(goal));

        memberGoalService.deleteGoal(memberId, goalId);

        verify(eventPublisher).publishEvent(new MemberGoalDeletedEvent(goalId));
        verify(memberGoalRepository).delete(goal);
    }

    @Test
    void updateGoalUsesPessimisticLockAndUpdatesEntity() {
        Long memberId = 1L;
        Long goalId = 10L;
        MemberGoal goal = MemberGoal.builder()
                .memberId(memberId).title("Java").startDate(LocalDate.of(2026, 2, 1))
                .targetDate(LocalDate.of(2026, 2, 2)).currentLevel("BEGINNER")
                .dailyStudyHours(1).availableDays(List.of("MON")).focusArea("backend").build();
        given(memberGoalRepository.findByIdForUpdate(goalId)).willReturn(Optional.of(goal));

        MemberStudyInfoRequestDto updateRequest = request(
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(10), List.of("TUE", "THU")
        );
        memberGoalService.updateGoal(memberId, goalId, updateRequest);

        verify(memberGoalRepository).findByIdForUpdate(goalId);
    }

    private MemberStudyInfoRequestDto request(LocalDate start, LocalDate target, List<String> days) {
        return new MemberStudyInfoRequestDto("Java", start, target, "BEGINNER", 1, days, "backend");
    }
}
