package com.steadyteller.backend.statistics.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.statistics.dto.DailyStatisticsResponse;
import com.steadyteller.backend.statistics.dto.GoalStatisticsResponse;
import com.steadyteller.backend.statistics.dto.StatisticsSummaryResponse;
import com.steadyteller.backend.statistics.exception.StatisticsErrorCode;
import com.steadyteller.backend.statistics.port.StatisticsQueryPort;
import com.steadyteller.backend.statistics.port.dto.StudyStatisticsRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StatisticsService {

    private final StatisticsQueryPort statisticsQueryPort;

    public StatisticsService(Optional<StatisticsQueryPort> statisticsQueryPort) {
        this.statisticsQueryPort = statisticsQueryPort.orElse(null);
    }

    public StatisticsSummaryResponse getSummary(Long memberId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        validateDataSource();
        List<StudyStatisticsRow> rows =
                statisticsQueryPort.findByMemberIdAndDateRange(memberId, startDate, endDate);

        long completedCount = rows.stream().filter(StudyStatisticsRow::completed).count();
        int totalPlannedMinutes = rows.stream().mapToInt(StudyStatisticsRow::plannedMinutes).sum();

        return new StatisticsSummaryResponse(
                startDate,
                endDate,
                rows.size(),
                completedCount,
                percentage(completedCount, rows.size()),
                totalPlannedMinutes,
                consecutiveStudyDays(rows, endDate)
        );
    }

    public List<DailyStatisticsResponse> getDaily(
            Long memberId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateDateRange(startDate, endDate);
        validateDataSource();
        return statisticsQueryPort.findByMemberIdAndDateRange(memberId, startDate, endDate).stream()
                .collect(Collectors.groupingBy(StudyStatisticsRow::scheduledDate))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toDailyResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    public GoalStatisticsResponse getGoalStatistics(Long memberId, Long goalId) {
        validateDataSource();
        List<StudyStatisticsRow> rows = statisticsQueryPort.findByMemberIdAndGoalId(memberId, goalId);
        long completedCount = rows.stream().filter(StudyStatisticsRow::completed).count();

        return new GoalStatisticsResponse(
                goalId,
                rows.size(),
                completedCount,
                percentage(completedCount, rows.size()),
                rows.stream().mapToInt(StudyStatisticsRow::plannedMinutes).sum()
        );
    }

    private DailyStatisticsResponse toDailyResponse(
            LocalDate studyDate,
            List<StudyStatisticsRow> rows
    ) {
        return new DailyStatisticsResponse(
                studyDate,
                rows.size(),
                rows.stream().filter(StudyStatisticsRow::completed).count(),
                percentage(rows.stream().filter(StudyStatisticsRow::completed).count(), rows.size()),
                rows.stream().mapToInt(StudyStatisticsRow::plannedMinutes).sum()
        );
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new CustomException(StatisticsErrorCode.INVALID_DATE_RANGE);
        }
    }

    private void validateDataSource() {
        if (statisticsQueryPort == null) {
            throw new CustomException(StatisticsErrorCode.DATA_SOURCE_NOT_CONNECTED);
        }
    }

    private double percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return round((double) numerator / denominator * 100.0);
    }

    private int consecutiveStudyDays(List<StudyStatisticsRow> rows, LocalDate endDate) {
        Set<LocalDate> studiedDates = rows.stream()
                .filter(StudyStatisticsRow::completed)
                .map(StudyStatisticsRow::scheduledDate)
                .collect(Collectors.toSet());

        int consecutiveDays = 0;
        LocalDate date = endDate;
        while (studiedDates.contains(date)) {
            consecutiveDays++;
            date = date.minusDays(1);
        }
        return consecutiveDays;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
