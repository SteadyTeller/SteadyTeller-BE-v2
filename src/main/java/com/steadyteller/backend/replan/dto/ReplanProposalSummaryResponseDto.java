package com.steadyteller.backend.replan.dto;

import com.steadyteller.backend.member.dto.AvailabilityResponse;
import java.time.LocalDateTime;
import java.util.List;

public record ReplanProposalSummaryResponseDto(Long proposalId, List<AvailabilityResponse> availabilities,
                                               int candidateCount, LocalDateTime createdAt) {
}
