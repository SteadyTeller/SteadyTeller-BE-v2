package com.steadyteller.backend.schedule.dto;

public record ScheduleFailureResultDto(int taskFailureCount, boolean deferredToSupplement,
                                       boolean supplementCapacityInsufficient, String recommendation) { }
