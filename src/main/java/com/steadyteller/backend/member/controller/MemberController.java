package com.steadyteller.backend.member.controller;

import com.steadyteller.backend.global.common.ApiResponse;
import com.steadyteller.backend.member.dto.AvailabilityReplaceRequest;
import com.steadyteller.backend.member.dto.AvailabilityResponse;
import com.steadyteller.backend.member.dto.LearningProfileResponse;
import com.steadyteller.backend.member.dto.LearningProfileUpdateRequest;
import com.steadyteller.backend.member.dto.MemberResponse;
import com.steadyteller.backend.member.dto.MemberUpdateRequest;
import com.steadyteller.backend.member.service.AvailabilityService;
import com.steadyteller.backend.member.service.LearningProfileService;
import com.steadyteller.backend.member.service.MemberService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members/me")
public class MemberController {
    private final MemberService memberService;
    private final LearningProfileService learningProfileService;
    private final AvailabilityService availabilityService;

    @GetMapping
    public ResponseEntity<ApiResponse<MemberResponse>> getMember(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(memberService.getMember(memberId)));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<MemberResponse>> updateMember(@AuthenticationPrincipal Long memberId,
                                                                      @Valid @RequestBody MemberUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(memberService.updateMember(memberId, request)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal Long memberId) {
        memberService.withdraw(memberId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/learning-profile")
    public ResponseEntity<ApiResponse<LearningProfileResponse>> getLearningProfile(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(learningProfileService.getProfile(memberId)));
    }

    @PutMapping("/learning-profile")
    public ResponseEntity<ApiResponse<LearningProfileResponse>> updateLearningProfile(@AuthenticationPrincipal Long memberId,
                                                                                         @Valid @RequestBody LearningProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(learningProfileService.updateProfile(memberId, request)));
    }

    @GetMapping("/availabilities")
    public ResponseEntity<ApiResponse<List<AvailabilityResponse>>> getAvailabilities(
            @AuthenticationPrincipal Long memberId, @RequestParam Long goalId) {
        return ResponseEntity.ok(ApiResponse.success(availabilityService.getAvailabilities(memberId, goalId)));
    }

    @PutMapping("/availabilities")
    public ResponseEntity<ApiResponse<List<AvailabilityResponse>>> replaceAvailabilities(
            @AuthenticationPrincipal Long memberId, @RequestParam Long goalId,
            @Valid @RequestBody AvailabilityReplaceRequest request) {
        return ResponseEntity.ok(ApiResponse.success(availabilityService.replaceAvailabilities(memberId, goalId, request)));
    }
}
