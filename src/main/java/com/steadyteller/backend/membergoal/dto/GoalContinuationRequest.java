package com.steadyteller.backend.membergoal.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Additional weekly time windows are registered through /members/me/availabilities before this call. */
public record GoalContinuationRequest(@NotNull @Future LocalDate newTargetDate) { }
