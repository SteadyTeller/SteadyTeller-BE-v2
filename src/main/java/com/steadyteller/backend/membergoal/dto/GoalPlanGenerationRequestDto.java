package com.steadyteller.backend.membergoal.dto;

import com.steadyteller.backend.member.dto.AvailabilityRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** One atomic request for goal settings, weekly availability, tasks, and the first schedule. */
public record GoalPlanGenerationRequestDto(
        @NotNull @Valid MemberStudyInfoRequestDto goal,
        @NotEmpty List<@Valid AvailabilityRequest> availabilities
) {
}
