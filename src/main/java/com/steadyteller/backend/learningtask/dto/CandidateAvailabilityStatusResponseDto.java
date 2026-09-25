package com.steadyteller.backend.learningtask.dto;

public record CandidateAvailabilityStatusResponseDto(
        int totalCandidateAllocatedMinutes,
        int totalAvailableMinutes,
        boolean isWithinAvailability
) { }
