package com.steadyteller.backend.schedule.dto;

import com.steadyteller.backend.schedule.entity.FailureReasonCode;
import jakarta.validation.constraints.NotNull;

public record ScheduleItemFailureRequestDto(@NotNull FailureReasonCode reasonCode, String reasonDetail) { }
