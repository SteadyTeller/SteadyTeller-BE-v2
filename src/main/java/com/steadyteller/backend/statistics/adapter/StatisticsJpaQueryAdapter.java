package com.steadyteller.backend.statistics.adapter;

import com.steadyteller.backend.schedule.entity.ScheduleItemStatus;
import com.steadyteller.backend.statistics.port.StatisticsQueryPort;
import com.steadyteller.backend.statistics.port.dto.StudyStatisticsRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Schedule과 ScheduleItem을 읽어 1차 MVP 통계 데이터를 구성합니다.
 */
@Repository
@RequiredArgsConstructor
public class StatisticsJpaQueryAdapter implements StatisticsQueryPort {

    private static final String BASE_SELECT = """
            SELECT si.id, s.goalId, si.date, si.allocatedMinutes, si.status
            FROM ScheduleItem si
            JOIN si.schedule s
            """;

    private final EntityManager entityManager;

    @Override
    public List<StudyStatisticsRow> findByMemberIdAndDateRange(
            Long memberId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return entityManager.createQuery(BASE_SELECT + """
                        WHERE s.memberId = :memberId
                          AND si.date BETWEEN :startDate AND :endDate
                        ORDER BY si.date, si.orderIndex
                        """, Object[].class)
                .setParameter("memberId", memberId)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getResultList().stream()
                .map(this::toStatisticsRow)
                .toList();
    }

    @Override
    public List<StudyStatisticsRow> findByMemberIdAndGoalId(Long memberId, Long goalId) {
        return entityManager.createQuery(BASE_SELECT + """
                        WHERE s.memberId = :memberId
                          AND s.goalId = :goalId
                        ORDER BY si.date, si.orderIndex
                        """, Object[].class)
                .setParameter("memberId", memberId)
                .setParameter("goalId", goalId)
                .getResultList().stream()
                .map(this::toStatisticsRow)
                .toList();
    }

    private StudyStatisticsRow toStatisticsRow(Object[] values) {
        ScheduleItemStatus status = (ScheduleItemStatus) values[4];
        return new StudyStatisticsRow(
                (Long) values[0],
                (Long) values[1],
                (LocalDate) values[2],
                (Integer) values[3],
                status == ScheduleItemStatus.FINISHED
        );
    }
}
