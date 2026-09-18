package com.steadyteller.backend.learningtask.dto;

import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.entity.ScheduleItemStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/** A concrete time placement of a task. A task may have zero or many placements. */
public record TaskSchedulePlacementResponseDto(
        Long scheduleId, Long scheduleItemId, LocalDate date, LocalTime startTime, LocalTime endTime,
        int allocatedMinutes, ScheduleItemStatus status
) {
    public static TaskSchedulePlacementResponseDto from(ScheduleItem item) {
        return new TaskSchedulePlacementResponseDto(item.getSchedule().getId(), item.getId(), item.getDate(),
                item.getStartTime(), item.getEndTime(), item.getAllocatedMinutes(), item.getStatus());
    }
}
