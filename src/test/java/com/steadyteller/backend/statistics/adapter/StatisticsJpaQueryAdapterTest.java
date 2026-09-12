package com.steadyteller.backend.statistics.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import com.steadyteller.backend.statistics.port.dto.StudyStatisticsRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StatisticsJpaQueryAdapterTest {

    @Autowired
    private StatisticsJpaQueryAdapter adapter;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleItemRepository scheduleItemRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("회원과 기간에 해당하는 스케줄 항목만 통계 데이터로 조회한다")
    void findsRowsWithinMemberAndDateRange() {
        LocalDate date = LocalDate.of(2026, 9, 10);
        Schedule ownedSchedule = saveSchedule(1L, 10L, date);
        Schedule otherSchedule = saveSchedule(2L, 20L, date);
        ScheduleItem ownedItem = saveItem(ownedSchedule, 101L, date, 45);
        saveItem(otherSchedule, 201L, date, 60);

        List<StudyStatisticsRow> rows = adapter.findByMemberIdAndDateRange(
                1L, date.minusDays(1), date.plusDays(1));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).scheduleItemId()).isEqualTo(ownedItem.getId());
        assertThat(rows.get(0).goalId()).isEqualTo(10L);
        assertThat(rows.get(0).plannedMinutes()).isEqualTo(45);
    }

    @Test
    @DisplayName("FINISHED 스케줄 항목을 완료된 통계 데이터로 변환한다")
    void mapsFinishedItemToCompletedRow() {
        LocalDate date = LocalDate.of(2026, 9, 10);
        Schedule schedule = saveSchedule(1L, 10L, date);
        ScheduleItem item = saveItem(schedule, 101L, date, 45);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE schedule_item SET status = 'FINISHED' WHERE id = :id")
                .setParameter("id", item.getId())
                .executeUpdate();
        entityManager.clear();

        List<StudyStatisticsRow> rows = adapter.findByMemberIdAndGoalId(1L, 10L);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.completed()).isTrue();
            assertThat(row.scheduledDate()).isEqualTo(date);
        });
    }

    private Schedule saveSchedule(Long memberId, Long goalId, LocalDate date) {
        return scheduleRepository.save(Schedule.create(memberId, goalId, date, date));
    }

    private ScheduleItem saveItem(Schedule schedule, Long taskId, LocalDate date, int minutes) {
        return scheduleItemRepository.save(ScheduleItem.create(
                schedule, taskId, "학습 태스크", date, date.getDayOfWeek(), minutes, 1));
    }
}
