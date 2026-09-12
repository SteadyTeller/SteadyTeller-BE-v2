package com.steadyteller.backend.schedule.event;

import com.steadyteller.backend.membergoal.event.MemberGoalDeletedEvent;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * MemberGoal 삭제 이벤트를 수신하여 해당 목표에 속한 모든 스케줄 및 스케줄 항목을 연쇄 정리한다.
 * ScheduleService.deleteSchedule과 동일하게 개별 삭제(deleteAll) + flush 후 부모를 삭제한다.
 * 벌크 JPQL 삭제(deleteByScheduleId)는 영속성 컨텍스트를 거치지 않아 부모 삭제와의 실행 순서가
 * DB/드라이버에 따라 달라질 수 있어(ScheduleService.deleteSchedule에서 FK 에러로 이미 한 번 발견됨),
 * 두 삭제 경로를 일부러 동일한 방식으로 통일한다.
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
            List<ScheduleItem> items =
                    scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(schedule.getId());
            scheduleItemRepository.deleteAll(items);
            scheduleItemRepository.flush();
            scheduleRepository.delete(schedule);
        }
    }
}
