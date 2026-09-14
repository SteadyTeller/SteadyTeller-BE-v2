package com.steadyteller.backend.statistics.dto;

import java.time.LocalDate;
public record StatisticsSummaryResponse(
        LocalDate startDate,
        LocalDate endDate,
        long totalSchedules,
        long completedSchedules,
        double completionRate,
        int totalPlannedMinutes,
        int consecutiveStudyDays
) {
}
