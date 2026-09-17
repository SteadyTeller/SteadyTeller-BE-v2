package com.steadyteller.backend.schedule.controller;

import com.steadyteller.backend.global.common.ApiResponse;
import com.steadyteller.backend.schedule.dto.ScheduleItemResponseDto;
import com.steadyteller.backend.schedule.dto.ScheduleItemFailureRequestDto;
import com.steadyteller.backend.schedule.dto.ScheduleFailureResultDto;
import com.steadyteller.backend.schedule.dto.ScheduleItemUpdateRequestDto;
import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;
import com.steadyteller.backend.schedule.dto.ScheduleSummaryDto;
import com.steadyteller.backend.schedule.service.ScheduleService;
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
 * 스케줄(Schedule) 리소스의 CRUD API. goalId 기준 생성/목록과, scheduleId 기준 단건 조회를
 * 하나의 컨트롤러에서 다룬다 (MemberController가 회원/학습프로필/가용시간을 한 컨트롤러에서
 * 다루는 것과 동일한 컨벤션).
 */
@RestController
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping("/api/v1/goals/{goalId}/schedules")
    public ResponseEntity<ApiResponse<ScheduleResponseDto>> generateSchedule(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long goalId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "학습 스케줄이 생성되었습니다.",
                scheduleService.generateSchedule(memberId, goalId)
        ));
    }

    @GetMapping("/api/v1/goals/{goalId}/schedules")
    public ResponseEntity<ApiResponse<List<ScheduleSummaryDto>>> getSchedules(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long goalId
    ) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.listSchedules(memberId, goalId)));
    }

    @GetMapping("/api/v1/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleResponseDto>> getSchedule(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId
    ) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getSchedule(memberId, scheduleId)));
    }

    @DeleteMapping("/api/v1/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId
    ) {
        scheduleService.deleteSchedule(memberId, scheduleId);
        return ResponseEntity.ok(ApiResponse.success("스케줄이 삭제되었습니다.", null));
    }

    @PatchMapping("/api/v1/schedules/{scheduleId}/items/{itemId}")
    public ResponseEntity<ApiResponse<ScheduleResponseDto>> updateScheduleItem(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId,
            @PathVariable Long itemId,
            @Valid @RequestBody ScheduleItemUpdateRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "스케줄 항목이 재배치되었습니다.",
                scheduleService.updateScheduleItem(memberId, scheduleId, itemId, request)
        ));
    }

    @PatchMapping("/api/v1/schedules/{scheduleId}/items/{itemId}/start")
    public ResponseEntity<ApiResponse<ScheduleItemResponseDto>> startScheduleItem(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId,
            @PathVariable Long itemId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "스케줄 항목 학습을 시작했습니다.",
                scheduleService.startScheduleItem(memberId, scheduleId, itemId)
        ));
    }

    @PatchMapping("/api/v1/schedules/{scheduleId}/items/{itemId}/complete")
    public ResponseEntity<ApiResponse<ScheduleItemResponseDto>> completeScheduleItem(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId,
            @PathVariable Long itemId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "스케줄 항목을 완료 처리했습니다.",
                scheduleService.completeScheduleItem(memberId, scheduleId, itemId)
        ));
    }

    @PatchMapping("/api/v1/schedules/{scheduleId}/items/{itemId}/failure")
    public ResponseEntity<ApiResponse<ScheduleFailureResultDto>> failScheduleItem(
            @AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId, @PathVariable Long itemId,
            @Valid @RequestBody ScheduleItemFailureRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success("실패 기록과 후속 처리 제안이 저장되었습니다.",
                scheduleService.failScheduleItem(memberId, scheduleId, itemId, request)));
    }

    @PatchMapping("/api/v1/schedules/{scheduleId}/items/{itemId}/defer-to-supplement")
    public ResponseEntity<ApiResponse<ScheduleItemResponseDto>> deferToSupplement(
            @AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId, @PathVariable Long itemId
    ) {
        return ResponseEntity.ok(ApiResponse.success("가장 빠른 보충일로 일정을 미뤘습니다.",
                scheduleService.deferToSupplement(memberId, scheduleId, itemId)));
    }

    @PostMapping("/api/v1/schedules/{scheduleId}/replan-remaining")
    public ResponseEntity<ApiResponse<ScheduleResponseDto>> replanRemaining(
            @AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId
    ) {
        return ResponseEntity.ok(ApiResponse.success("완료한 태스크를 제외하고 남은 일정을 재조정했습니다.",
                scheduleService.replanRemaining(memberId, scheduleId)));
    }

    @DeleteMapping("/api/v1/schedules/{scheduleId}/items/{itemId}/complete")
    public ResponseEntity<ApiResponse<ScheduleItemResponseDto>> revertScheduleItemCompletion(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long scheduleId,
            @PathVariable Long itemId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "스케줄 항목의 완료 처리를 취소했습니다.",
                scheduleService.revertScheduleItemCompletion(memberId, scheduleId, itemId)
        ));
    }
}
