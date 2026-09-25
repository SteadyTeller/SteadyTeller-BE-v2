package com.steadyteller.backend.replan.controller;

import com.steadyteller.backend.global.common.ApiResponse;
import com.steadyteller.backend.learningtask.dto.CandidateAvailabilityStatusResponseDto;
import com.steadyteller.backend.learningtask.dto.LearningTaskCandidateRequestDto;
import com.steadyteller.backend.member.dto.AvailabilityReplaceRequest;
import com.steadyteller.backend.replan.dto.ReplanProposalSummaryResponseDto;
import com.steadyteller.backend.replan.dto.ReplanTaskCandidateResponseDto;
import com.steadyteller.backend.replan.service.ReplanService;
import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/goals/{goalId}/replan")
@RequiredArgsConstructor
public class ReplanController {
    private final ReplanService replanService;

    @PutMapping("/availabilities")
    public ResponseEntity<ApiResponse<ReplanProposalSummaryResponseDto>> createProposal(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId,
            @Valid @RequestBody AvailabilityReplaceRequest request) {
        return ResponseEntity.ok(ApiResponse.success(replanService.createProposal(memberId, goalId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ReplanProposalSummaryResponseDto>> getSummary(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId) {
        return ResponseEntity.ok(ApiResponse.success(replanService.getSummary(memberId, goalId)));
    }

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<List<ReplanTaskCandidateResponseDto>>> getCandidates(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId) {
        return ResponseEntity.ok(ApiResponse.success(replanService.getCandidates(memberId, goalId)));
    }

    @GetMapping("/tasks/capacity-status")
    public ResponseEntity<ApiResponse<CandidateAvailabilityStatusResponseDto>> getCapacity(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId) {
        return ResponseEntity.ok(ApiResponse.success(replanService.getCapacity(memberId, goalId)));
    }

    @PostMapping("/tasks")
    public ResponseEntity<ApiResponse<ReplanTaskCandidateResponseDto>> addCandidate(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId,
            @Valid @RequestBody LearningTaskCandidateRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(replanService.addCandidate(memberId, goalId, request)));
    }

    @PatchMapping("/tasks/{candidateId}")
    public ResponseEntity<ApiResponse<ReplanTaskCandidateResponseDto>> updateCandidate(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId, @PathVariable Long candidateId,
            @Valid @RequestBody LearningTaskCandidateRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(replanService.updateCandidate(memberId, goalId, candidateId, request)));
    }

    @DeleteMapping("/tasks/{candidateId}")
    public ResponseEntity<ApiResponse<Void>> deleteCandidate(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId, @PathVariable Long candidateId) {
        replanService.deleteCandidate(memberId, goalId, candidateId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<ScheduleResponseDto>> confirm(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId) {
        return ResponseEntity.ok(ApiResponse.success(replanService.confirm(memberId, goalId)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> cancel(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId) {
        replanService.cancel(memberId, goalId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
