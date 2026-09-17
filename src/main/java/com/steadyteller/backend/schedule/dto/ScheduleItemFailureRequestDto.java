package com.steadyteller.backend.schedule.dto;

import com.steadyteller.backend.schedule.entity.FailureHandlingAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScheduleItemFailureRequestDto(@NotBlank String reasonCode, String reasonDetail,
                                            @NotNull FailureHandlingAction action) { }
