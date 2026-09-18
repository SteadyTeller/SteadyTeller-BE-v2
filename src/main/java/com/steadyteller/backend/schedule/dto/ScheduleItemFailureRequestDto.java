package com.steadyteller.backend.schedule.dto;

import com.steadyteller.backend.schedule.entity.FailureHandlingAction;

import jakarta.validation.constraints.NotNull;

public record ScheduleItemFailureRequestDto(@jakarta.validation.constraints.NotNull com.steadyteller.backend.schedule.entity.FailureReasonCode reasonCode, String reasonDetail,
                                            @NotNull FailureHandlingAction action) { }
