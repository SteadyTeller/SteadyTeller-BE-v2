package com.steadyteller.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.learningtask.entity.LearningTaskStatus;
import com.steadyteller.backend.learningtask.repository.LearningTaskRepository;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.exception.GoalErrorCode;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.schedule.dto.DailyScheduleDto;
import com.steadyteller.backend.schedule.dto.ScheduleItemUpdateRequestDto;
import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;
import com.steadyteller.backend.schedule.dto.ScheduleSummaryDto;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.exception.ScheduleErrorCode;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private MemberGoalRepository memberGoalRepository;

    @Mock
    private LearningTaskRepository learningTaskRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleItemRepository scheduleItemRepository;

    @Mock
    private ScheduleAiService scheduleAiService;

    // 배정 알고리즘 자체는 ScheduleAllocatorTest에서 이미 검증하므로, 여기서는 mock이 아닌 실제 구현을 사용해
    // ScheduleService가 그 결과를 올바르게 저장/변환하는지에 집중한다.
    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(
                memberGoalRepository, learningTaskRepository, scheduleRepository,
                scheduleItemRepository, scheduleAiService
        );
    }

    @Test
    void generatesScheduleFromConfirmedTasksRespectingCapacity() {
        Long memberId = 1L;
        Long goalId = 10L;
        MemberGoal goal = goal(memberId, List.of("MON", "WED"));
        LearningTask task1 = task(1L, "정규화 기초", 45);
        LearningTask task2 = task(2L, "정규화 심화", 45);

        LocalDate date1 = LocalDate.of(2026, 8, 24);
        LocalDate date2 = LocalDate.of(2026, 8, 26);
        List<ScheduleAllocator.AllocatedItem> allocations = List.of(
                new ScheduleAllocator.AllocatedItem(task1, date1, 45, 1),
                new ScheduleAllocator.AllocatedItem(task2, date2, 45, 1)
        );

        given(memberGoalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(learningTaskRepository.findByGoalIdAndStatusForUpdate(goalId, LearningTaskStatus.PENDING))
                .willReturn(List.of(task1, task2));
        given(scheduleAiService.generateSchedule(any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
                .willReturn(allocations);
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(invocation -> {
            Schedule schedule = invocation.getArgument(0);
            ReflectionTestUtils.setField(schedule, "id", 100L);
            return schedule;
        });
        given(scheduleItemRepository.saveAll(org.mockito.ArgumentMatchers.<List<ScheduleItem>>any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        ScheduleResponseDto response = scheduleService.generateSchedule(memberId, goalId);

        assertThat(response.scheduleId()).isEqualTo(100L);
        assertThat(response.goalId()).isEqualTo(goalId);
        // 45분 + 45분 = 90분 > 60분(2시간 아님, dailyStudyHours=1이므로 60분) 이므로 서로 다른 날에 배정된다.
        assertThat(response.dailySchedules()).hasSize(2);
        DailyScheduleDto firstDay = response.dailySchedules().get(0);
        assertThat(firstDay.items()).hasSize(1);
        assertThat(firstDay.totalAllocatedMinutes()).isEqualTo(45);

        // 스케줄 생성 후 태스크의 상태가 SCHEDULED로 전이되었는지 확인
        assertThat(task1.getStatus()).isEqualTo(LearningTaskStatus.SCHEDULED);
        assertThat(task2.getStatus()).isEqualTo(LearningTaskStatus.SCHEDULED);
    }

    @Test
    void throwsWhenGoalNotFound() {
        given(memberGoalRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.generateSchedule(1L, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(GoalErrorCode.GOAL_NOT_FOUND);
        verifyNoInteractions(learningTaskRepository, scheduleRepository, scheduleItemRepository);
    }

    @Test
    void throwsWhenGoalNotOwnedByMember() {
        MemberGoal goal = goal(2L, List.of("MON"));
        given(memberGoalRepository.findById(10L)).willReturn(Optional.of(goal));

        assertThatThrownBy(() -> scheduleService.generateSchedule(1L, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(GoalErrorCode.GOAL_ACCESS_DENIED);
    }

    @Test
    void throwsWhenNoConfirmedTasksExist() {
        Long memberId = 1L;
        Long goalId = 10L;
        MemberGoal goal = goal(memberId, List.of("MON"));
        given(memberGoalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(learningTaskRepository.findByGoalIdAndStatusForUpdate(goalId, LearningTaskStatus.PENDING))
                .willReturn(List.of());

        assertThatThrownBy(() -> scheduleService.generateSchedule(memberId, goalId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.NO_CONFIRMED_TASKS);
    }

    @Test
    void throwsWhenConfirmedTaskHasNonPositiveAllocatedMinutes() {
        // 검토 단계에서 사용자가 후보를 직접 수정(PATCH)해 0 이하의 시간을 넣는 경우를 방어한다.
        Long memberId = 1L;
        Long goalId = 10L;
        MemberGoal goal = goal(memberId, List.of("MON"));
        LearningTask invalidTask = task(1L, "잘못된 태스크", 0);
        given(memberGoalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(learningTaskRepository.findByGoalIdAndStatusForUpdate(goalId, LearningTaskStatus.PENDING))
                .willReturn(List.of(invalidTask));

        assertThatThrownBy(() -> scheduleService.generateSchedule(memberId, goalId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.INVALID_LEARNING_TASK_DURATION);
    }

    @Test
    void throwsWhenConfirmedTaskHasAllocatedMinutesExceedingMax() {
        // 검토 단계에서 사용자가 1440분을 초과하는 시간을 넣는 경우를 방어한다.
        Long memberId = 1L;
        Long goalId = 10L;
        MemberGoal goal = goal(memberId, List.of("MON"));
        LearningTask invalidTask = task(1L, "너무 긴 태스크", 1441);
        given(memberGoalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(learningTaskRepository.findByGoalIdAndStatusForUpdate(goalId, LearningTaskStatus.PENDING))
                .willReturn(List.of(invalidTask));

        assertThatThrownBy(() -> scheduleService.generateSchedule(memberId, goalId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.INVALID_LEARNING_TASK_DURATION);
    }

    @Test
    void throwsWhenDailyStudyHoursIsNullOrNonPositive() {
        Long memberId = 1L;
        Long goalId = 10L;
        LearningTask task1 = task(1L, "정규화 기초", 30);

        // 1. null인 경우
        MemberGoal nullHoursGoal = MemberGoal.builder()
                .memberId(memberId).title("목표").startDate(LocalDate.now()).targetDate(LocalDate.now().plusMonths(1))
                .currentLevel("초급").dailyStudyHours(null).availableDays(List.of("MON")).focusArea("DB").build();
        ReflectionTestUtils.setField(nullHoursGoal, "id", goalId);
        given(memberGoalRepository.findById(goalId)).willReturn(Optional.of(nullHoursGoal));
        given(learningTaskRepository.findByGoalIdAndStatusForUpdate(goalId, LearningTaskStatus.PENDING))
                .willReturn(List.of(task1));

        assertThatThrownBy(() -> scheduleService.generateSchedule(memberId, goalId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.INVALID_DAILY_STUDY_HOURS);

        // 2. 0인 경우
        MemberGoal zeroHoursGoal = MemberGoal.builder()
                .memberId(memberId).title("목표").startDate(LocalDate.now()).targetDate(LocalDate.now().plusMonths(1))
                .currentLevel("초급").dailyStudyHours(0).availableDays(List.of("MON")).focusArea("DB").build();
        ReflectionTestUtils.setField(zeroHoursGoal, "id", goalId);
        given(memberGoalRepository.findById(goalId)).willReturn(Optional.of(zeroHoursGoal));

        assertThatThrownBy(() -> scheduleService.generateSchedule(memberId, goalId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.INVALID_DAILY_STUDY_HOURS);

        // 3. 음수인 경우
        MemberGoal negativeHoursGoal = MemberGoal.builder()
                .memberId(memberId).title("목표").startDate(LocalDate.now()).targetDate(LocalDate.now().plusMonths(1))
                .currentLevel("초급").dailyStudyHours(-2).availableDays(List.of("MON")).focusArea("DB").build();
        ReflectionTestUtils.setField(negativeHoursGoal, "id", goalId);
        given(memberGoalRepository.findById(goalId)).willReturn(Optional.of(negativeHoursGoal));

        assertThatThrownBy(() -> scheduleService.generateSchedule(memberId, goalId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.INVALID_DAILY_STUDY_HOURS);
    }

    @Test
    void throwsWhenAvailableDaysContainInvalidValue() {
        Long memberId = 1L;
        Long goalId = 10L;
        MemberGoal goal = goal(memberId, List.of("MON", "HOLIDAY"));
        LearningTask task1 = task(1L, "정규화 기초", 30);
        given(memberGoalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(learningTaskRepository.findByGoalIdAndStatusForUpdate(goalId, LearningTaskStatus.PENDING))
                .willReturn(List.of(task1));

        assertThatThrownBy(() -> scheduleService.generateSchedule(memberId, goalId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.INVALID_AVAILABLE_DAYS);
    }

    @Test
    void getScheduleReturnsScheduleGroupedByDate() {
        Long memberId = 1L;
        Schedule schedule = schedule(100L, memberId, 10L);
        ScheduleItem item = scheduleItem(1001L, schedule, 1L, "정규화 기초", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 30, 1);
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(100L)).willReturn(List.of(item));

        ScheduleResponseDto response = scheduleService.getSchedule(memberId, 100L);

        assertThat(response.scheduleId()).isEqualTo(100L);
        assertThat(response.dailySchedules()).hasSize(1);
    }

    @Test
    void getScheduleThrowsWhenNotFound() {
        given(scheduleRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getSchedule(1L, 100L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    void getScheduleThrowsWhenNotOwnedByMember() {
        Schedule schedule = schedule(100L, 2L, 10L);
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> scheduleService.getSchedule(1L, 100L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_ACCESS_DENIED);
    }

    @Test
    void listSchedulesReturnsSummariesForGoal() {
        Long memberId = 1L;
        Long goalId = 10L;
        MemberGoal goal = goal(memberId, List.of("MON"));
        Schedule schedule = schedule(100L, memberId, goalId);
        given(memberGoalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(scheduleRepository.findByGoalIdOrderByStartDateDesc(goalId)).willReturn(List.of(schedule));
        given(scheduleItemRepository.countByScheduleId(100L)).willReturn(3L);

        List<ScheduleSummaryDto> result = scheduleService.listSchedules(memberId, goalId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).scheduleId()).isEqualTo(100L);
        assertThat(result.get(0).totalItems()).isEqualTo(3L);
    }

    @Test
    void listSchedulesReturnsEmptyListWhenGoalHasNoSchedules() {
        Long memberId = 1L;
        Long goalId = 10L;
        MemberGoal goal = goal(memberId, List.of("MON"));
        given(memberGoalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(scheduleRepository.findByGoalIdOrderByStartDateDesc(goalId)).willReturn(List.of());

        List<ScheduleSummaryDto> result = scheduleService.listSchedules(memberId, goalId);

        assertThat(result).isEmpty();
    }

    @Test
    void deleteScheduleRemovesScheduleAndItsItemsAndRevertsTasksToPending() {
        Long memberId = 1L;
        Schedule schedule = schedule(100L, memberId, 10L);
        LearningTask scheduledTask = task(1L, "정규화 기초", 30);
        scheduledTask.markAsScheduled();
        ScheduleItem item = scheduleItem(1001L, schedule, 1L, "정규화 기초", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 30, 1);

        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(100L)).willReturn(List.of(item));
        given(learningTaskRepository.findAllById(List.of(1L))).willReturn(List.of(scheduledTask));

        scheduleService.deleteSchedule(memberId, 100L);

        assertThat(scheduledTask.getStatus()).isEqualTo(LearningTaskStatus.PENDING);
        verify(scheduleItemRepository, times(1)).deleteAll(List.of(item));
        verify(scheduleRepository, times(1)).delete(schedule);
    }

    @Test
    void deleteScheduleThrowsWhenNotOwnedByMember() {
        Schedule schedule = schedule(100L, 2L, 10L);
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> scheduleService.deleteSchedule(1L, 100L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_ACCESS_DENIED);
        verify(scheduleItemRepository, never()).deleteAll(any());
        verify(scheduleRepository, never()).delete(any(Schedule.class));
    }

    @Test
    void updateScheduleItemMovesToNewAvailableDateAndReordersBothDates() {
        // Given: 월요일에 항목 2개(순서 1,2), 수요일에 항목 1개(순서 1)가 있는 상태에서
        // 월요일의 두 번째 항목(orderInDay=2)을 수요일로 옮긴다.
        Long memberId = 1L;
        Long goalId = 10L;
        MemberGoal goal = goal(memberId, List.of("MON", "WED"));
        Schedule schedule = schedule(100L, memberId, goalId);
        ScheduleItem mondayFirst = scheduleItem(1001L, schedule, 1L, "정규화 기초", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 20, 1);
        ScheduleItem mondaySecond = scheduleItem(1002L, schedule, 2L, "정규화 심화", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 20, 2);
        ScheduleItem wednesdayFirst = scheduleItem(1003L, schedule, 3L, "SQL 기초", LocalDate.of(2026, 9, 16), DayOfWeek.WEDNESDAY, 20, 1);
        given(memberGoalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(100L))
                .willReturn(List.of(mondayFirst, mondaySecond, wednesdayFirst));

        LocalDate wednesday = LocalDate.of(2026, 9, 16);
        ScheduleResponseDto response = scheduleService.updateScheduleItem(
                memberId, 100L, 1002L, new ScheduleItemUpdateRequestDto(wednesday, null)
        );

        // Then: 월요일에는 mondayFirst 혼자 남아 order=1 유지, 수요일에는 wednesdayFirst(order=1) 뒤에
        // 옮겨진 항목(order=2)이 이어붙는다.
        assertThat(mondaySecond.getDate()).isEqualTo(wednesday);
        assertThat(mondaySecond.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(mondayFirst.getOrderIndex()).isEqualTo(1);
        assertThat(wednesdayFirst.getOrderIndex()).isEqualTo(1);
        assertThat(mondaySecond.getOrderIndex()).isEqualTo(2);
        assertThat(response.dailySchedules()).hasSize(2);
    }

    @Test
    void updateScheduleItemToDateWithNoExistingItemsStartsOrderAtOne() {
        // Given: 목표(월,수,금) 중 아직 아무 항목도 없는 금요일로 항목을 옮긴다.
        Long memberId = 1L;
        Long goalId = 10L;
        MemberGoal goal = goal(memberId, List.of("MON", "WED", "FRI"));
        Schedule schedule = schedule(100L, memberId, goalId);
        ScheduleItem mondayOnly = scheduleItem(1001L, schedule, 1L, "정규화 기초", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 20, 1);
        given(memberGoalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(100L))
                .willReturn(List.of(mondayOnly));

        LocalDate friday = LocalDate.of(2026, 9, 18);
        scheduleService.updateScheduleItem(memberId, 100L, 1001L, new ScheduleItemUpdateRequestDto(friday, null));

        assertThat(mondayOnly.getDate()).isEqualTo(friday);
        assertThat(mondayOnly.getOrderIndex()).isEqualTo(1);
    }

    @Test
    void updateScheduleItemAllowsCapacityExactlyAtLimit() {
        // Given: dailyStudyHours=1 -> 60분 한도. 이동할 항목(30분) + 기존 항목(30분) = 정확히 60분(경계값, 허용).
        Long memberId = 1L;
        MemberGoal goal = goal(memberId, List.of("MON", "WED"));
        Schedule schedule = schedule(100L, memberId, 10L);
        ScheduleItem moving = scheduleItem(1001L, schedule, 1L, "정규화 기초", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 30, 1);
        ScheduleItem existingOnWednesday = scheduleItem(1002L, schedule, 2L, "SQL", LocalDate.of(2026, 9, 16), DayOfWeek.WEDNESDAY, 30, 1);
        given(memberGoalRepository.findById(10L)).willReturn(Optional.of(goal));
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(100L))
                .willReturn(List.of(moving, existingOnWednesday));

        LocalDate wednesday = LocalDate.of(2026, 9, 16);
        scheduleService.updateScheduleItem(memberId, 100L, 1001L, new ScheduleItemUpdateRequestDto(wednesday, null));

        assertThat(moving.getDate()).isEqualTo(wednesday);
        assertThat(existingOnWednesday.getOrderIndex()).isEqualTo(1);
        assertThat(moving.getOrderIndex()).isEqualTo(2);
    }

    @Test
    void updateScheduleItemThrowsWhenIncreasingMinutesOnSameDateExceedsCapacity() {
        // Given: 같은 날짜(월)에 두 항목(30분+20분=50분)이 있는데, 그중 하나를 45분으로 늘리면
        // 30+45=75분 > 60분 한도를 초과한다(날짜 이동 없이 시간만 변경하는 경우도 검증되어야 함).
        Long memberId = 1L;
        MemberGoal goal = goal(memberId, List.of("MON"));
        Schedule schedule = schedule(100L, memberId, 10L);
        ScheduleItem first = scheduleItem(1001L, schedule, 1L, "정규화 기초", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 30, 1);
        ScheduleItem second = scheduleItem(1002L, schedule, 2L, "정규화 심화", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 20, 2);
        given(memberGoalRepository.findById(10L)).willReturn(Optional.of(goal));
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(100L))
                .willReturn(List.of(first, second));

        assertThatThrownBy(() -> scheduleService.updateScheduleItem(
                memberId, 100L, 1002L, new ScheduleItemUpdateRequestDto(LocalDate.of(2026, 9, 14), 45)
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_ITEM_CAPACITY_EXCEEDED);
    }

    @Test
    void updateScheduleItemThrowsWhenDateIsInThePast() {
        Long memberId = 1L;
        MemberGoal goal = goal(memberId, List.of("MON"));
        Schedule schedule = schedule(100L, memberId, 10L);
        ScheduleItem item = scheduleItem(1001L, schedule, 1L, "정규화 기초", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 30, 1);
        given(memberGoalRepository.findById(10L)).willReturn(Optional.of(goal));
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(100L)).willReturn(List.of(item));

        assertThatThrownBy(() -> scheduleService.updateScheduleItem(
                memberId, 100L, 1001L, new ScheduleItemUpdateRequestDto(LocalDate.now().minusDays(1), null)
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_ITEM_DATE_IN_PAST);
    }

    @Test
    void updateScheduleItemThrowsWhenDateNotInAvailableDays() {
        Long memberId = 1L;
        MemberGoal goal = goal(memberId, List.of("MON")); // 화요일은 가용 요일 아님
        Schedule schedule = schedule(100L, memberId, 10L);
        ScheduleItem item = scheduleItem(1001L, schedule, 1L, "정규화 기초", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 30, 1);
        given(memberGoalRepository.findById(10L)).willReturn(Optional.of(goal));
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(100L)).willReturn(List.of(item));

        assertThatThrownBy(() -> scheduleService.updateScheduleItem(
                memberId, 100L, 1001L, new ScheduleItemUpdateRequestDto(LocalDate.of(2026, 9, 15), null)
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_ITEM_DATE_NOT_AVAILABLE);
    }

    @Test
    void updateScheduleItemThrowsWhenTargetDateCapacityExceeded() {
        Long memberId = 1L;
        MemberGoal goal = goal(memberId, List.of("MON", "WED")); // dailyStudyHours=1 -> 60분 한도
        Schedule schedule = schedule(100L, memberId, 10L);
        ScheduleItem moving = scheduleItem(1001L, schedule, 1L, "정규화 기초", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 30, 1);
        ScheduleItem existingOnWednesday = scheduleItem(1002L, schedule, 2L, "SQL", LocalDate.of(2026, 9, 16), DayOfWeek.WEDNESDAY, 50, 1);
        given(memberGoalRepository.findById(10L)).willReturn(Optional.of(goal));
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(100L))
                .willReturn(List.of(moving, existingOnWednesday));

        // 30(moving) + 50(existing) = 80분 > 60분 한도
        assertThatThrownBy(() -> scheduleService.updateScheduleItem(
                memberId, 100L, 1001L, new ScheduleItemUpdateRequestDto(LocalDate.of(2026, 9, 16), null)
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_ITEM_CAPACITY_EXCEEDED);
    }

    @Test
    void updateScheduleItemThrowsWhenItemNotFound() {
        Long memberId = 1L;
        MemberGoal goal = goal(memberId, List.of("MON"));
        Schedule schedule = schedule(100L, memberId, 10L);
        given(memberGoalRepository.findById(10L)).willReturn(Optional.of(goal));
        given(scheduleRepository.findById(100L)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(100L)).willReturn(List.of());

        assertThatThrownBy(() -> scheduleService.updateScheduleItem(
                memberId, 100L, 9999L, new ScheduleItemUpdateRequestDto(LocalDate.of(2026, 9, 14), null)
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_ITEM_NOT_FOUND);
    }

    private Schedule schedule(Long id, Long memberId, Long goalId) {
        Schedule schedule = Schedule.create(memberId, goalId, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16));
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }

    private ScheduleItem scheduleItem(Long id, Schedule schedule, Long learningTaskId, String title,
                                       LocalDate date, DayOfWeek dayOfWeek, int allocatedMinutes, int orderIndex) {
        ScheduleItem item = ScheduleItem.create(schedule, learningTaskId, title, date, dayOfWeek, allocatedMinutes, orderIndex);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private MemberGoal goal(Long memberId, List<String> availableDays) {
        MemberGoal goal = MemberGoal.builder()
                .memberId(memberId)
                .title("정보처리기사 합격하기")
                .startDate(LocalDate.now().minusDays(1))
                .targetDate(LocalDate.now().plusMonths(2))
                .currentLevel("초급")
                .dailyStudyHours(1)
                .availableDays(availableDays)
                .focusArea("데이터베이스 정규화")
                .build();
        ReflectionTestUtils.setField(goal, "id", 10L);
        return goal;
    }

    private LearningTask task(Long id, String title, int allocatedMinutes) {
        LearningTask task = LearningTask.builder()
                .goalId(10L)
                .title(title)
                .category("데이터베이스")
                .subject("정규화")
                .importance(3)
                .difficulty(3)
                .allocatedMinutes(allocatedMinutes)
                .source(LearningTaskSource.AI_GENERATED)
                .isModified(false)
                .build();
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }
}
