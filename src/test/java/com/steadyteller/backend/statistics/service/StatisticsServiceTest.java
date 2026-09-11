package com.steadyteller.backend.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.statistics.dto.DailyStatisticsResponse;
import com.steadyteller.backend.statistics.dto.GoalStatisticsResponse;
import com.steadyteller.backend.statistics.dto.StatisticsSummaryResponse;
import com.steadyteller.backend.statistics.exception.StatisticsErrorCode;
import com.steadyteller.backend.statistics.port.StatisticsQueryPort;
import com.steadyteller.backend.statistics.port.dto.StudyStatisticsRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock private StatisticsQueryPort statisticsQueryPort;
    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsService(Optional.of(statisticsQueryPort));
    }

    @Test
    @DisplayName("FINISHED 항목을 기준으로 완료율과 계획시간을 계산한다")
    void calculatesSummaryFromScheduleItemStatus() {
        LocalDate startDate = LocalDate.of(2026, 9, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 3);
        when(statisticsQueryPort.findByMemberIdAndDateRange(1L, startDate, endDate))
                .thenReturn(List.of(
                        row(1L, 10L, startDate, 60, true),
                        row(2L, 10L, startDate.plusDays(1), 60, false),
                        row(3L, 10L, endDate, 60, false)));

        StatisticsSummaryResponse response = statisticsService.getSummary(1L, startDate, endDate);

        assertThat(response.totalSchedules()).isEqualTo(3);
        assertThat(response.completedSchedules()).isEqualTo(1);
        assertThat(response.completionRate()).isEqualTo(33.33);
        assertThat(response.totalPlannedMinutes()).isEqualTo(180);
    }

    @Test
    @DisplayName("종료일부터 완료 항목이 연속된 날짜 수를 계산한다")
    void calculatesConsecutiveStudyDays() {
        LocalDate startDate = LocalDate.of(2026, 9, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 4);
        when(statisticsQueryPort.findByMemberIdAndDateRange(1L, startDate, endDate))
                .thenReturn(List.of(
                        row(1L, 10L, endDate.minusDays(2), 30, true),
                        row(2L, 10L, endDate.minusDays(1), 30, true),
                        row(3L, 10L, endDate, 30, true)));

        assertThat(statisticsService.getSummary(1L, startDate, endDate).consecutiveStudyDays())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("일별 통계를 날짜순으로 묶고 일별 완료율을 계산한다")
    void groupsDailyStatisticsInDateOrder() {
        LocalDate startDate = LocalDate.of(2026, 9, 1);
        LocalDate endDate = LocalDate.of(2026, 9, 2);
        when(statisticsQueryPort.findByMemberIdAndDateRange(1L, startDate, endDate))
                .thenReturn(List.of(
                        row(2L, 10L, endDate, 40, true),
                        row(1L, 10L, startDate, 30, false),
                        row(3L, 10L, endDate, 20, false)));

        List<DailyStatisticsResponse> responses = statisticsService.getDaily(1L, startDate, endDate);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).studyDate()).isEqualTo(startDate);
        assertThat(responses.get(1).scheduleCount()).isEqualTo(2);
        assertThat(responses.get(1).completedCount()).isEqualTo(1);
        assertThat(responses.get(1).completionRate()).isEqualTo(50.0);
        assertThat(responses.get(1).plannedMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("목표 진행률은 로그인 회원과 목표 ID를 함께 조건으로 조회한다")
    void getsGoalProgressWithinMemberScope() {
        when(statisticsQueryPort.findByMemberIdAndGoalId(7L, 20L)).thenReturn(List.of(
                row(1L, 20L, LocalDate.of(2026, 9, 1), 50, true),
                row(2L, 20L, LocalDate.of(2026, 9, 2), 50, false)));

        GoalStatisticsResponse response = statisticsService.getGoalStatistics(7L, 20L);

        assertThat(response.goalId()).isEqualTo(20L);
        assertThat(response.progressRate()).isEqualTo(50.0);
        verify(statisticsQueryPort).findByMemberIdAndGoalId(7L, 20L);
    }

    @Test
    @DisplayName("통계 데이터가 없으면 비율을 0으로 반환한다")
    void returnsZeroForEmptyStatistics() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        when(statisticsQueryPort.findByMemberIdAndDateRange(1L, date, date))
                .thenReturn(List.of());

        StatisticsSummaryResponse response = statisticsService.getSummary(1L, date, date);

        assertThat(response.totalSchedules()).isZero();
        assertThat(response.completionRate()).isZero();
        assertThat(response.consecutiveStudyDays()).isZero();
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 요청을 거절한다")
    void rejectsInvalidDateRange() {
        assertThatThrownBy(() -> statisticsService.getSummary(
                1L, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(StatisticsErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    @DisplayName("스케줄링 데이터 연결 전에는 명확한 오류를 반환한다")
    void rejectsRequestBeforeDataSourceIsConnected() {
        StatisticsService disconnectedService = new StatisticsService(Optional.empty());

        assertThatThrownBy(() -> disconnectedService.getGoalStatistics(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(StatisticsErrorCode.DATA_SOURCE_NOT_CONNECTED);
    }

    private StudyStatisticsRow row(Long itemId, Long goalId, LocalDate date,
                                   int plannedMinutes, boolean completed) {
        return new StudyStatisticsRow(itemId, goalId, date, plannedMinutes, completed);
    }
}
