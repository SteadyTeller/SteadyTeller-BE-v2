package com.steadyteller.backend.schedule.event;

import com.steadyteller.backend.membergoal.event.MemberGoalDeletedEvent;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * MemberGoal 삭제 이벤트를 수신하여 해당 목표에 속한 모든 스케줄 및 스케줄 항목을 연쇄 정리한다.
 */
@Component
@RequiredArgsConstructor
public class ScheduleGoalEventListener {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository scheduleItemRepository;

    @EventListener
    @Transactional
    public void handleGoalDeleted(MemberGoalDeletedEvent event) {
        List<Schedule> schedules = scheduleRepository.findByGoalIdOrderByStartDateDesc(event.goalId());
        for (Schedule schedule : schedules) {
            scheduleItemRepository.deleteByScheduleId(schedule.getId());
            scheduleRepository.delete(schedule);
        }
    }
}
