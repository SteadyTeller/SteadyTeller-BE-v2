package com.steadyteller.backend.schedule.controller;

import com.steadyteller.backend.global.common.ApiResponse;
import com.steadyteller.backend.schedule.dto.ScheduleFailureResultDto;
import com.steadyteller.backend.schedule.dto.ScheduleItemFailureRequestDto;
import com.steadyteller.backend.schedule.dto.ScheduleItemResponseDto;
import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;
import com.steadyteller.backend.schedule.dto.ScheduleSummaryDto;
import com.steadyteller.backend.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ScheduleController {
    private final ScheduleService scheduleService;

    @GetMapping("/api/v1/goals/{goalId}/schedules")
    public ResponseEntity<ApiResponse<List<ScheduleSummaryDto>>> getSchedules(
            @AuthenticationPrincipal Long memberId, @PathVariable Long goalId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.listSchedules(memberId, goalId)));
    }

    @GetMapping("/api/v1/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleResponseDto>> getSchedule(
            @AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getSchedule(memberId, scheduleId)));
    }

    @PatchMapping("/api/v1/schedules/{scheduleId}/items/{itemId}/start")
    public ResponseEntity<ApiResponse<ScheduleItemResponseDto>> start(
            @AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId, @PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.startScheduleItem(memberId, scheduleId, itemId)));
    }

    @PatchMapping("/api/v1/schedules/{scheduleId}/items/{itemId}/complete")
    public ResponseEntity<ApiResponse<ScheduleItemResponseDto>> complete(
            @AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId, @PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.completeScheduleItem(memberId, scheduleId, itemId)));
    }

    @PatchMapping("/api/v1/schedules/{scheduleId}/items/{itemId}/failure")
    public ResponseEntity<ApiResponse<ScheduleFailureResultDto>> fail(
            @AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId, @PathVariable Long itemId,
            @Valid @RequestBody ScheduleItemFailureRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.failScheduleItem(memberId, scheduleId, itemId, request)));
    }

    @DeleteMapping("/api/v1/schedules/{scheduleId}/items/{itemId}/complete")
    public ResponseEntity<ApiResponse<ScheduleItemResponseDto>> revertCompletion(
            @AuthenticationPrincipal Long memberId, @PathVariable Long scheduleId, @PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.revertScheduleItemCompletion(memberId, scheduleId, itemId)));
    }
}
