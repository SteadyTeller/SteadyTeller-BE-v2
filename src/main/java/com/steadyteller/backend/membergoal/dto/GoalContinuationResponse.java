package com.steadyteller.backend.membergoal.dto;

public record GoalContinuationResponse(Long goalId, java.time.LocalDate newTargetDate,
                                       int remainingTaskCount) { }
