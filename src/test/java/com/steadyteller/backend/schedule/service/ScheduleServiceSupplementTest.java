package com.steadyteller.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceSupplementTest {

    @Mock private MemberGoalRepository memberGoalRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private ScheduleItemRepository scheduleItemRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    @Test
    void supplementSlotBugDemo() {
        Long memberId = 1L;
        Long scheduleId = 100L;
        Schedule schedule = Schedule.create(memberId, 10L, LocalDate.now(), LocalDate.now().plusDays(2));
        ReflectionTestUtils.setField(schedule, "id", scheduleId);

        // A 30-min regular item
        ScheduleItem source = ScheduleItem.create(schedule, 1L, "Task 1", LocalDate.now(), DayOfWeek.MONDAY, 30, 1);
        ReflectionTestUtils.setField(source, "id", 1001L);
        
        // A 60-min supplement slot
        ScheduleItem supplement = ScheduleItem.createSupplement(schedule, LocalDate.now().plusDays(1), DayOfWeek.TUESDAY, 60, 1);
        ReflectionTestUtils.setField(supplement, "id", 1002L);

        given(scheduleRepository.findById(scheduleId)).willReturn(Optional.of(schedule));
        given(scheduleItemRepository.findByIdAndScheduleIdForUpdate(1001L, scheduleId)).willReturn(Optional.of(source));
        given(scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAscForUpdate(scheduleId))
                .willReturn(new java.util.ArrayList<>(List.of(source, supplement)));

        // When
        scheduleService.deferToSupplement(memberId, scheduleId, 1001L);

        // Then 
        assertThat(supplement.getLearningTaskId()).isEqualTo(1L);
        assertThat(supplement.getAllocatedMinutes()).isEqualTo(30); // Fixed: Should be exactly the source's minutes
        
        assertThat(source.getLearningTaskId()).isNull();
        assertThat(source.getAllocatedMinutes()).isEqualTo(30); // The remaining empty slot is 30 mins.
        
        System.out.println("Test passed. The bug is fixed!");
    }
}
