package com.steadyteller.backend.membergoal.dto;

import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;

public record GoalContinuationResponse(Long goalId, java.time.LocalDate newTargetDate,
                                       int remainingTaskCount, ScheduleResponseDto schedule) { }
