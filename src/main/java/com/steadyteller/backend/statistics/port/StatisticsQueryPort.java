package com.steadyteller.backend.statistics.port;

import com.steadyteller.backend.statistics.port.dto.StudyStatisticsRow;
import java.time.LocalDate;
import java.util.List;

/**
 * 통계 계산에 필요한 학습 데이터를 조회하는 경계입니다.
 * 통계 도메인은 스케줄링/수행 기록 도메인의 엔티티나 Repository에 직접 의존하지 않습니다.
 */
public interface StatisticsQueryPort {

    List<StudyStatisticsRow> findByMemberIdAndDateRange(
            Long memberId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<StudyStatisticsRow> findByMemberIdAndGoalId(Long memberId, Long goalId);
}
