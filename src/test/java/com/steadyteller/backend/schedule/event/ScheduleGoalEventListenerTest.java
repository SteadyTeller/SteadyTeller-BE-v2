package com.steadyteller.backend.schedule.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.steadyteller.backend.membergoal.event.MemberGoalDeletedEvent;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduleGoalEventListenerTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleItemRepository scheduleItemRepository;

    @InjectMocks
    private ScheduleGoalEventListener listener;

    @Test
    void deletesScheduleItemsAndSchedulesWhenGoalDeletedEventReceived() {
        Long goalId = 10L;
        Schedule schedule1 = Schedule.create(1L, goalId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10));
        ReflectionTestUtils.setField(schedule1, "id", 100L);
        ScheduleItem item = ScheduleItem.create(
                schedule1, 1L, "항목", LocalDate.of(2026, 9, 1), DayOfWeek.TUESDAY, 30, 1);

        given(scheduleRepository.findByGoalIdOrderByStartDateDesc(goalId)).willReturn(List.of(schedule1));
        given(scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAscForUpdate(100L)).willReturn(List.of(item));

        listener.handleGoalDeleted(new MemberGoalDeletedEvent(goalId));

        // ScheduleService.deleteSchedule과 동일하게 deleteAll + flush로 개별 삭제한 뒤 부모를 지우는지 검증한다
        // (deleteByScheduleId 벌크 삭제는 부모 삭제와의 실행 순서가 어긋나 FK 에러가 났던 전례가 있어 사용하지 않는다).
        verify(scheduleItemRepository).deleteAll(List.of(item));
        verify(scheduleItemRepository).flush();
        verify(scheduleRepository).delete(schedule1);
    }
}
