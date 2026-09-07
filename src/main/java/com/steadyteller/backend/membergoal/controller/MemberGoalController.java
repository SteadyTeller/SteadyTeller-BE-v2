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
@RequestMapping("/goals")
@RequiredArgsConstructor
public class MemberGoalController {

    private final MemberGoalService memberGoalService;

    // 학습 목표 설정: 새 MemberGoal 생성
    @PostMapping
    public ResponseEntity<ApiResponse<MemberGoalResponseDto>> createGoal(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody MemberStudyInfoRequestDto request) {
        MemberGoalResponseDto response = memberGoalService.createGoal(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("학습 목표가 생성되었습니다.", response));
    }

    // 내가 가진 학습 목표 전체 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<MemberGoalResponseDto>>> getGoals(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(memberGoalService.getGoals(memberId)));
    }

    // 학습 목표 단건 조회
    @GetMapping("/{goalId}")
    public ResponseEntity<ApiResponse<MemberGoalResponseDto>> getGoal(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long goalId) {
        return ResponseEntity.ok(ApiResponse.success(memberGoalService.getGoal(memberId, goalId)));
    }

    // 학습 목표 수정
    @PatchMapping("/{goalId}")
    public ResponseEntity<ApiResponse<MemberGoalResponseDto>> updateGoal(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long goalId,
            @Valid @RequestBody MemberStudyInfoRequestDto request) {
        MemberGoalResponseDto response = memberGoalService.updateGoal(memberId, goalId, request);
        return ResponseEntity.ok(ApiResponse.success("학습 목표가 수정되었습니다.", response));
    }

    // 학습 목표 삭제
    @DeleteMapping("/{goalId}")
    public ResponseEntity<ApiResponse<Void>> deleteGoal(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long goalId) {
        memberGoalService.deleteGoal(memberId, goalId);
        return ResponseEntity.ok(ApiResponse.success("학습 목표가 삭제되었습니다.", null));
    }
}
