package com.steadyteller.backend.schedule.dto;

import jakarta.validation.constraints.NotNull;

public record ScheduleItemFailureRequestDto(
        @NotNull com.steadyteller.backend.schedule.entity.FailureReasonCode reasonCode,
        String reasonDetail
) { }
