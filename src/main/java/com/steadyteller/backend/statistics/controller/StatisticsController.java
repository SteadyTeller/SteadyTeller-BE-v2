package com.steadyteller.backend.statistics.controller;

import com.steadyteller.backend.global.common.ApiResponse;
import com.steadyteller.backend.statistics.dto.DailyStatisticsResponse;
import com.steadyteller.backend.statistics.dto.GoalStatisticsResponse;
import com.steadyteller.backend.statistics.dto.StatisticsSummaryResponse;
import com.steadyteller.backend.statistics.service.StatisticsService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<StatisticsSummaryResponse>> getSummary(
            @AuthenticationPrincipal Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                statisticsService.getSummary(memberId, startDate, endDate)
        ));
    }

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<List<DailyStatisticsResponse>>> getDaily(
            @AuthenticationPrincipal Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                statisticsService.getDaily(memberId, startDate, endDate)
        ));
    }

    @GetMapping("/goals/{goalId}")
    public ResponseEntity<ApiResponse<GoalStatisticsResponse>> getGoalStatistics(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long goalId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                statisticsService.getGoalStatistics(memberId, goalId)
        ));
    }
}
