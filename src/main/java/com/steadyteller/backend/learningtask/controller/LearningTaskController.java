package com.steadyteller.backend.learningtask.controller;

import com.steadyteller.backend.global.common.ApiResponse;
import com.steadyteller.backend.learningtask.dto.LearningTaskCandidateRequestDto;
import com.steadyteller.backend.learningtask.dto.LearningTaskCandidateResponseDto;
import com.steadyteller.backend.learningtask.dto.LearningTaskResponseDto;
import com.steadyteller.backend.learningtask.service.LearningTaskService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 세부 태스크 생성(설계 명세 2번) ~ 학습 항목 검토(설계 명세 3번) API.
 * 승인(confirm) 전까지는 후보(candidate) 목록만 다루며 DB에 저장되지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class LearningTaskController {

    private final LearningTaskService learningTaskService;

    // AI 세부 태스크 생성: MemberGoal을 컨텍스트로 AI 호출 → 후보 목록을 캐시에 저장하고 반환 (기존 후보는 교체됨)
    @PostMapping("/goals/{goalId}/tasks/generate")
    public ResponseEntity<ApiResponse<List<LearningTaskCandidateResponseDto>>> generateTasks(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long goalId) {
        List<LearningTaskCandidateResponseDto> response = learningTaskService.generateTasks(memberId, goalId);
        return ResponseEntity.ok(ApiResponse.success("AI가 세부 태스크를 생성했습니다.", response));
    }

    // 현재 검토 대상인 후보 태스크 목록 조회 (아직 DB에 저장되지 않은 상태)
    @GetMapping("/goals/{goalId}/tasks")
    public ResponseEntity<ApiResponse<List<LearningTaskCandidateResponseDto>>> getCandidates(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long goalId) {
        return ResponseEntity.ok(ApiResponse.success(learningTaskService.getCandidates(memberId, goalId)));
    }

    // 사용자가 후보 목록에 태스크를 직접 추가 (source=USER_ADDED)
    @PostMapping("/goals/{goalId}/tasks")
    public ResponseEntity<ApiResponse<LearningTaskCandidateResponseDto>> addCandidate(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long goalId,
            @Valid @RequestBody LearningTaskCandidateRequestDto request) {
        LearningTaskCandidateResponseDto response = learningTaskService.addUserCandidate(memberId, goalId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    // 후보 태스크 내용 수정 (isModified=true로 반영)
    @PatchMapping("/tasks/{taskId}")
    public ResponseEntity<ApiResponse<LearningTaskCandidateResponseDto>> updateCandidate(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long taskId,
            @Valid @RequestBody LearningTaskCandidateRequestDto request) {
        LearningTaskCandidateResponseDto response = learningTaskService.updateCandidate(memberId, taskId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 후보 목록에서 제거 (=거부. DB에 저장된 적 없으므로 그냥 후보에서 빠짐)
    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<ApiResponse<Void>> deleteCandidate(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long taskId) {
        learningTaskService.deleteCandidate(memberId, taskId);
        return ResponseEntity.ok(ApiResponse.success("후보 태스크가 삭제되었습니다.", null));
    }

    // 최종 승인: 현재 후보 목록을 LearningTask로 일괄 저장 (status=PENDING) 후 후보 캐시 비움
    @PostMapping("/goals/{goalId}/tasks/confirm")
    public ResponseEntity<ApiResponse<List<LearningTaskResponseDto>>> confirmTasks(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long goalId) {
        List<LearningTaskResponseDto> response = learningTaskService.confirmTasks(memberId, goalId);
        return ResponseEntity.ok(ApiResponse.success("학습 태스크가 확정되었습니다.", response));
    }
}
