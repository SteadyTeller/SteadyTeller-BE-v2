package com.steadyteller.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
        given(learningTaskRepository.findByGoalIdAndStatus(goalId, LearningTaskStatus.PENDING))
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
        given(learningTaskRepository.findByGoalIdAndStatus(goalId, LearningTaskStatus.PENDING))
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
        given(learningTaskRepository.findByGoalIdAndStatus(goalId, LearningTaskStatus.PENDING))
                .willReturn(List.of(invalidTask));

        assertThatThrownBy(() -> scheduleService.generateSchedule(memberId, goalId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.INVALID_LEARNING_TASK_DURATION);
    }

    @Test
    void throwsWhenAvailableDaysContainInvalidValue() {
        Long memberId = 1L;
        Long goalId = 10L;
        MemberGoal goal = goal(memberId, List.of("MON", "HOLIDAY"));
        LearningTask task1 = task(1L, "정규화 기초", 30);
        given(memberGoalRepository.findById(goalId)).willReturn(Optional.of(goal));
        given(learningTaskRepository.findByGoalIdAndStatus(goalId, LearningTaskStatus.PENDING))
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
