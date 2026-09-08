package com.steadyteller.backend.schedule.controller;

import com.steadyteller.backend.global.common.ApiResponse;
import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;
import com.steadyteller.backend.schedule.dto.ScheduleSummaryDto;
import com.steadyteller.backend.schedule.service.ScheduleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
}
