package com.steadyteller.backend.statistics.dto;

public record GoalStatisticsResponse(
        Long goalId,
        long totalSchedules,
        long completedSchedules,
        double progressRate,
        int totalPlannedMinutes
) {
}
