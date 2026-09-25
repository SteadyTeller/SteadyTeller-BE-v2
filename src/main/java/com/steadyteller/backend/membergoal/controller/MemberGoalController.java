package com.steadyteller.backend.membergoal.controller;

import com.steadyteller.backend.global.common.ApiResponse;
import com.steadyteller.backend.membergoal.dto.MemberGoalResponseDto;
import com.steadyteller.backend.membergoal.dto.MemberStudyInfoRequestDto;
import com.steadyteller.backend.membergoal.service.MemberGoalService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class MemberGoalController {
    private final MemberGoalService memberGoalService;

    @PostMapping
    public ResponseEntity<ApiResponse<MemberGoalResponseDto>> createGoal(
            @AuthenticationPrincipal Long memberId, @Valid @RequestBody MemberStudyInfoRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(memberGoalService.createGoal(memberId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MemberGoalResponseDto>>> getGoals(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(memberGoalService.getGoals(memberId)));
    }

    @GetMapping("/{goalId}")
    public ResponseEntity<ApiResponse<MemberGoalResponseDto>> getGoal(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId) {
        return ResponseEntity.ok(ApiResponse.success(memberGoalService.getGoal(memberId, goalId)));
    }

    @PatchMapping("/{goalId}")
    public ResponseEntity<ApiResponse<MemberGoalResponseDto>> updateGoal(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId,
            @Valid @RequestBody MemberStudyInfoRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(memberGoalService.updateGoal(memberId, goalId, request)));
    }

    @DeleteMapping("/{goalId}")
    public ResponseEntity<ApiResponse<Void>> deleteGoal(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId) {
        memberGoalService.deleteGoal(memberId, goalId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
