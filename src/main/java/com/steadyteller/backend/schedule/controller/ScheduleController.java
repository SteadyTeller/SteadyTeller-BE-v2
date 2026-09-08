package com.steadyteller.backend.schedule.controller;

import com.steadyteller.backend.global.common.ApiResponse;
import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;
import com.steadyteller.backend.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/goals/{goalId}/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleResponseDto>> generateSchedule(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long goalId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "학습 스케줄이 생성되었습니다.",
                scheduleService.generateSchedule(memberId, goalId)
        ));
    }
}
