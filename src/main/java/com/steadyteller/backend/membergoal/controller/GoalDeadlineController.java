package com.steadyteller.backend.membergoal.controller;

import com.steadyteller.backend.global.common.ApiResponse;
import com.steadyteller.backend.membergoal.dto.GoalDeadlineResponse;
import com.steadyteller.backend.membergoal.dto.GoalContinuationRequest;
import com.steadyteller.backend.membergoal.dto.GoalContinuationResponse;
import jakarta.validation.Valid;
import com.steadyteller.backend.membergoal.service.GoalDeadlineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GoalDeadlineController {
    private final GoalDeadlineService goalDeadlineService;

    @GetMapping("/api/v1/goals/{goalId}/deadline")
    public ResponseEntity<ApiResponse<GoalDeadlineResponse>> getDeadline(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId) {
        return ResponseEntity.ok(ApiResponse.success(goalDeadlineService.getDeadline(memberId, goalId)));
    }

    @PostMapping("/api/v1/goals/{goalId}/deadline/continue")
    public ResponseEntity<ApiResponse<GoalContinuationResponse>> continueGoal(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId,
            @Valid @RequestBody GoalContinuationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("목표 완료 예정일을 연장했습니다.",
                goalDeadlineService.continueGoal(memberId, goalId, request)));
    }
}
