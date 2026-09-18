package com.steadyteller.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.repository.MemberRepository;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import com.steadyteller.backend.schedule.dto.ScheduleItemResponseDto;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ScheduleSupplementEdgeCaseTest {

    @Autowired private ScheduleService scheduleService;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private ScheduleItemRepository scheduleItemRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberGoalRepository memberGoalRepository;

    @Test
    @DisplayName("deferToSupplement - remaining minutes split correctly and items ordered correctly")
    void deferToSupplementSplitEdgeCase() {
        // given
        Member member = memberRepository.save(Member.create("test@test.com", "pass", "test"));
        MemberGoal goal = memberGoalRepository.save(MemberGoal.builder()
                .memberId(member.getId())
                .title("Title")
                .startDate(LocalDate.now())
                .targetDate(LocalDate.now().plusDays(10))
                .currentLevel("B")
                .dailyStudyHours(2)
                .breakMinutes(0)
                .availableDays(List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"))
                .focusArea("Focus")
                .build());
        
        LocalDate startDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        Schedule schedule = scheduleRepository.save(Schedule.create(member.getId(), goal.getId(), startDate, startDate.plusDays(2)));
        
        // Target to defer (30 mins)
        ScheduleItem source = scheduleItemRepository.save(ScheduleItem.create(schedule, 10L, "Test Task", startDate, startDate.getDayOfWeek(), 30, 1));
        
        // Destination supplement (60 mins)
        LocalDate tomorrow = startDate.plusDays(1);
        ScheduleItem dest = scheduleItemRepository.save(ScheduleItem.createSupplement(schedule, tomorrow, tomorrow.getDayOfWeek(), 60, 1));
        
        scheduleItemRepository.flush();
        scheduleRepository.flush();

        // when
        ScheduleItemResponseDto dto = scheduleService.deferToSupplement(member.getId(), schedule.getId(), source.getId());
        
        // then
        assertThat(dto.allocatedMinutes()).isEqualTo(30);
        
        List<ScheduleItem> items = scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAscForUpdate(schedule.getId());
        
        // Should be 3 items:
        // 1. source -> turned into supplement on startDate (30 min)
        // 2. destination -> assigned the task on tomorrow (30 min)
        // 3. new supplement -> remaining time on tomorrow (30 min)
        assertThat(items).hasSize(3);
        
        List<ScheduleItem> tomorrowItems = items.stream().filter(i -> i.getDate().equals(tomorrow)).toList();
        assertThat(tomorrowItems).hasSize(2);
        
        ScheduleItem assigned = tomorrowItems.get(0);
        assertThat(assigned.getLearningTaskId()).isEqualTo(10L);
        assertThat(assigned.getAllocatedMinutes()).isEqualTo(30);
        assertThat(assigned.getOrderIndex()).isEqualTo(1);
        
        ScheduleItem remainder = tomorrowItems.get(1);
        assertThat(remainder.getLearningTaskId()).isNull();
        assertThat(remainder.getAllocatedMinutes()).isEqualTo(30);
        assertThat(remainder.getOrderIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("deferToSupplement - exactly fits supplement slot (remaining minutes is 0)")
    void deferToSupplementExactFitEdgeCase() {
        // given
        Member member = memberRepository.save(Member.create("test2@test.com", "pass", "test2"));
        MemberGoal goal = memberGoalRepository.save(MemberGoal.builder()
                .memberId(member.getId())
                .title("Title 2")
                .startDate(LocalDate.now())
                .targetDate(LocalDate.now().plusDays(10))
                .currentLevel("B")
                .dailyStudyHours(2)
                .breakMinutes(0)
                .availableDays(List.of("MONDAY", "TUESDAY", "WEDNESDAY"))
                .focusArea("Focus")
                .build());
        
        LocalDate startDate = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        Schedule schedule = scheduleRepository.save(Schedule.create(member.getId(), goal.getId(), startDate, startDate.plusDays(2)));
        
        // Target to defer (60 mins)
        ScheduleItem source = scheduleItemRepository.save(ScheduleItem.create(schedule, 20L, "Test Task 2", startDate, startDate.getDayOfWeek(), 60, 1));
        
        // Destination supplement (60 mins)
        LocalDate tomorrow = startDate.plusDays(1);
        ScheduleItem dest = scheduleItemRepository.save(ScheduleItem.createSupplement(schedule, tomorrow, tomorrow.getDayOfWeek(), 60, 1));
        
        scheduleItemRepository.flush();
        scheduleRepository.flush();

        // when
        ScheduleItemResponseDto dto = scheduleService.deferToSupplement(member.getId(), schedule.getId(), source.getId());
        
        // then
        assertThat(dto.allocatedMinutes()).isEqualTo(60);
        
        List<ScheduleItem> items = scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAscForUpdate(schedule.getId());
        
        // Should be 2 items:
        // 1. source -> turned into supplement on startDate (60 min)
        // 2. destination -> assigned the task on tomorrow (60 min)
        // No new supplement created!
        assertThat(items).hasSize(2);
        
        List<ScheduleItem> tomorrowItems = items.stream().filter(i -> i.getDate().equals(tomorrow)).toList();
        assertThat(tomorrowItems).hasSize(1);
        
        ScheduleItem assigned = tomorrowItems.get(0);
        assertThat(assigned.getLearningTaskId()).isEqualTo(20L);
        assertThat(assigned.getAllocatedMinutes()).isEqualTo(60);
    }
}
