package com.steadyteller.backend.statistics.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.steadyteller.backend.global.common.ApiResponse;
import com.steadyteller.backend.statistics.dto.GoalStatisticsResponse;
import com.steadyteller.backend.statistics.dto.StatisticsSummaryResponse;
import com.steadyteller.backend.statistics.service.StatisticsService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class StatisticsControllerTest {

    @Mock
    private StatisticsService statisticsService;

    @InjectMocks
    private StatisticsController statisticsController;

    @Test
    void summaryUsesAuthenticatedMemberId() {
        LocalDate startDate = LocalDate.of(2026, 9, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 7);
        StatisticsSummaryResponse expected = new StatisticsSummaryResponse(
                startDate, endDate, 2, 1, 50.0, 120, 1
        );
        when(statisticsService.getSummary(3L, startDate, endDate)).thenReturn(expected);

        ResponseEntity<ApiResponse<StatisticsSummaryResponse>> response =
                statisticsController.getSummary(3L, startDate, endDate);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(expected);
        verify(statisticsService).getSummary(3L, startDate, endDate);
    }

    @Test
    void goalStatisticsUsesAuthenticatedMemberIdAndPathGoalId() {
        GoalStatisticsResponse expected = new GoalStatisticsResponse(9L, 2, 1, 50.0, 60);
        when(statisticsService.getGoalStatistics(3L, 9L)).thenReturn(expected);

        ResponseEntity<ApiResponse<GoalStatisticsResponse>> response =
                statisticsController.getGoalStatistics(3L, 9L);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(expected);
        verify(statisticsService).getGoalStatistics(3L, 9L);
    }
}
