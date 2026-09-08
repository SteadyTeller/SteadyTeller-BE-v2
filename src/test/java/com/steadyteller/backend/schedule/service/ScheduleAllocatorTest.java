package com.steadyteller.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.schedule.exception.ScheduleErrorCode;
import com.steadyteller.backend.schedule.service.ScheduleAllocator.AllocatedItem;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleAllocatorTest {

    private final ScheduleAllocator allocator = new ScheduleAllocator();

    @Test
    void allocatesMultipleTasksToSameDayWhenCapacityAllows() {
        // 2026-08-24(월)는 가용 요일. 60분 용량에 30분짜리 두 개가 모두 들어간다.
        LearningTask task1 = task("A", 30);
        LearningTask task2 = task("B", 30);

        List<AllocatedItem> result = allocator.allocate(
                List.of(task1, task2),
                LocalDate.of(2026, 8, 24),
                Set.of(DayOfWeek.MONDAY),
                60,
                365
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(result.get(0).orderInDay()).isEqualTo(1);
        assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(result.get(1).orderInDay()).isEqualTo(2);
    }

    @Test
    void movesToNextAvailableDayWhenCapacityExceeded() {
        // 45분 + 45분 = 90분이므로 60분 한도를 초과 -> 다음 가용 요일(수)로 넘어간다.
        LearningTask task1 = task("A", 45);
        LearningTask task2 = task("B", 45);

        List<AllocatedItem> result = allocator.allocate(
                List.of(task1, task2),
                LocalDate.of(2026, 8, 24), // MON
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                60,
                365
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2026, 8, 26));
    }

    @Test
    void skipsUnavailableDaysWhenWalkingForward() {
        LearningTask task1 = task("A", 30);

        List<AllocatedItem> result = allocator.allocate(
                List.of(task1),
                LocalDate.of(2026, 8, 25), // 화 (가용 아님)
                Set.of(DayOfWeek.FRIDAY),
                60,
                365
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 8, 28)); // 금
    }

    @Test
    void placesOversizedSingleTaskAloneEvenIfExceedingDailyCapacity() {
        // 태스크 하나가 일일 한도(60분)보다 커도(90분) 그날 단독으로는 배정되어야 한다.
        // 그렇지 않으면 영원히 배정되지 못해 무한 루프에 빠진다.
        LearningTask oversized = task("긴 태스크", 90);
        LearningTask next = task("다음 태스크", 30);

        List<AllocatedItem> result = allocator.allocate(
                List.of(oversized, next),
                LocalDate.of(2026, 8, 24), // MON
                Set.of(DayOfWeek.MONDAY),
                60,
                365
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).task()).isEqualTo(oversized);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(result.get(1).task()).isEqualTo(next);
        assertThat(result.get(1).date()).isEqualTo(LocalDate.of(2026, 8, 31)); // 다음 주 월요일
    }

    @Test
    void throwsWhenHorizonExceeded() {
        LearningTask task1 = task("A", 30);

        assertThatThrownBy(() -> allocator.allocate(
                List.of(task1),
                LocalDate.of(2026, 8, 24),
                Set.of(DayOfWeek.SUNDAY), // 8/24(월)로부터 한참 뒤
                60,
                3
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ScheduleErrorCode.SCHEDULE_GENERATION_FAILED);
    }

    private LearningTask task(String title, int allocatedMinutes) {
        return LearningTask.builder()
                .goalId(1L)
                .title(title)
                .category("category")
                .subject("subject")
                .importance(3)
                .difficulty(3)
                .allocatedMinutes(allocatedMinutes)
                .source(LearningTaskSource.AI_GENERATED)
                .isModified(false)
                .build();
    }
}
