package com.steadyteller.backend.learningtask.dto;

/** Candidate-task usage against the goal's active availability until its deadline. */
public record CandidateAvailabilityStatusResponseDto(
        int totalCandidateAllocatedMinutes,
        int totalAvailableMinutes,
        boolean isWithinAvailability
) { }
