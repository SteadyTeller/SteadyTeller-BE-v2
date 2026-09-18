package com.steadyteller.backend.studytimer;
import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.schedule.exception.ScheduleErrorCode;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class TimerScheduleQueryAdapter implements TimerScheduleQueryPort {
    private final ScheduleRepository schedules;
    private final ScheduleItemRepository items;
    public Context findOwnedItemForUpdate(Long memberId, Long scheduleId, Long itemId) {
        var schedule = schedules.findById(scheduleId)
            .orElseThrow(() -> new CustomException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (!schedule.getMemberId().equals(memberId))
            throw new CustomException(ScheduleErrorCode.SCHEDULE_ACCESS_DENIED);
        var item = items.findByIdAndScheduleIdForUpdate(itemId, scheduleId)
            .orElseThrow(() -> new CustomException(ScheduleErrorCode.SCHEDULE_ITEM_NOT_FOUND));
        if (item.getLearningTaskId() == null) throw new CustomException(TimerErrorCode.EMPTY_ITEM);
        return new Context(schedule.getGoalId(), item.getLearningTaskId(), item.getTitle(), item.getAllocatedMinutes());
    }
}
