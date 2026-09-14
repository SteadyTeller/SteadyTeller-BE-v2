package com.steadyteller.backend.statistics.dto;

import java.time.LocalDate;

public record DailyStatisticsResponse(
        LocalDate studyDate,
        long scheduleCount,
        long completedCount,
        double completionRate,
        int plannedMinutes
) {
}
