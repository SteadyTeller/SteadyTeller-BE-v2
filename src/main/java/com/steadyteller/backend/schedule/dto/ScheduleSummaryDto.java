package com.steadyteller.backend.schedule.dto;

import com.steadyteller.backend.schedule.entity.Schedule;
import java.time.LocalDate;

public record ScheduleSummaryDto(
        Long scheduleId,
        Long goalId,
        LocalDate scheduleStartDate,
        LocalDate scheduleEndDate,
        long totalItems
) {
    public static ScheduleSummaryDto of(Schedule schedule, long totalItems) {
        return new ScheduleSummaryDto(
                schedule.getId(),
                schedule.getGoalId(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                totalItems
        );
    }
}
