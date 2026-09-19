package com.steadyteller.backend.studytimer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TimerResultPersistenceIntegrationTest {

    @Autowired
    private TimerResultService timerResultService;

    @Autowired
    private TimerResultRepository timerResultRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleItemRepository scheduleItemRepository;

    @Test
    void savesServerDerivedSnapshotAndReturnsSameRecordForIdenticalRetry() {
        ScheduleItem item = saveItem(1L, 10L, 101L);
        UUID attemptId = UUID.randomUUID();
        TimerResultRequest request = new TimerResultRequest(
                attemptId, 37, StudyResult.FAILED, "OTHER", "복습 시간이 더 필요함", "정규화 2단계", "3단계");

        TimerResult first = timerResultService.save(
                1L, item.getSchedule().getId(), item.getId(), request);
        TimerResult retried = timerResultService.save(
                1L, item.getSchedule().getId(), item.getId(), request);

        assertThat(retried.getId()).isEqualTo(first.getId());
        assertThat(timerResultRepository.count()).isEqualTo(1);
        assertThat(first.getGoalId()).isEqualTo(10L);
        assertThat(first.getLearningTaskId()).isEqualTo(101L);
        assertThat(first.getPlannedMinutes()).isEqualTo(45);
        assertThat(first.getActualMinutes()).isEqualTo(37);
        assertThat(first.getReasonDetail()).isEqualTo("복습 시간이 더 필요함");
    }

    @Test
    void doesNotSaveResultForAnotherMembersSchedule() {
        ScheduleItem item = saveItem(1L, 10L, 101L);
        TimerResultRequest request = new TimerResultRequest(
                UUID.randomUUID(), 30, StudyResult.COMPLETED, null, null, "정규화", null);

        assertThatThrownBy(() -> timerResultService.save(
                2L, item.getSchedule().getId(), item.getId(), request))
                .isInstanceOf(CustomException.class);
        assertThat(timerResultRepository.count()).isZero();
    }

    @Test
    void readsOnlyOwnedItemHistoryInNewestFirstOrder() {
        ScheduleItem item = saveItem(1L, 10L, 101L);
        TimerResultRequest first = new TimerResultRequest(
                UUID.randomUUID(), 20, StudyResult.FAILED, "OTHER", "첫 시도", "1단계", "2단계");
        TimerResultRequest second = new TimerResultRequest(
                UUID.randomUUID(), 35, StudyResult.COMPLETED, null, null, "2단계", null);
        timerResultService.save(1L, item.getSchedule().getId(), item.getId(), first);
        timerResultService.save(1L, item.getSchedule().getId(), item.getId(), second);

        var history = timerResultService.findHistory(1L, item.getSchedule().getId(), item.getId());

        assertThat(history).hasSize(2);
        assertThat(history).extracting(TimerResultResponse::actualMinutes).containsExactly(35, 20);
        assertThatThrownBy(() -> timerResultService.findHistory(
                2L, item.getSchedule().getId(), item.getId())).isInstanceOf(CustomException.class);
    }

    private ScheduleItem saveItem(Long memberId, Long goalId, Long learningTaskId) {
        LocalDate date = LocalDate.of(2026, 9, 19);
        Schedule schedule = scheduleRepository.save(Schedule.create(memberId, goalId, date, date));
        return scheduleItemRepository.saveAndFlush(ScheduleItem.create(
                schedule, learningTaskId, "데이터베이스 정규화", date, date.getDayOfWeek(), 45, 1));
    }
}
