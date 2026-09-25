package com.steadyteller.backend.member.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Complete desired availability state for one goal. */
public record AvailabilityReplaceRequest(@NotNull List<@Valid AvailabilityRequest> availabilities) {
}
