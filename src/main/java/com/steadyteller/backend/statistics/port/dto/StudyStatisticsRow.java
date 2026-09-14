package com.steadyteller.backend.statistics.port.dto;

import java.time.LocalDate;

/** 통계 도메인이 스케줄링 데이터에서 조회하는 1차 MVP용 최소 데이터입니다. */
public record StudyStatisticsRow(
        Long scheduleItemId,
        Long goalId,
        LocalDate scheduledDate,
        int plannedMinutes,
        boolean completed
) {
}
