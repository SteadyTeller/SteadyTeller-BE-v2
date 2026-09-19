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

    public Context findOwnedItem(Long memberId, Long scheduleId, Long itemId) {
        var schedule = findOwnedSchedule(memberId, scheduleId);
        var item = items.findByIdAndScheduleId(itemId, scheduleId)
            .orElseThrow(() -> new CustomException(ScheduleErrorCode.SCHEDULE_ITEM_NOT_FOUND));
        return toContext(schedule.getGoalId(), item.getLearningTaskId(), item.getTitle(), item.getAllocatedMinutes());
    }

    public Context findOwnedItemForUpdate(Long memberId, Long scheduleId, Long itemId) {
        var schedule = findOwnedSchedule(memberId, scheduleId);
        var item = items.findByIdAndScheduleIdForUpdate(itemId, scheduleId)
            .orElseThrow(() -> new CustomException(ScheduleErrorCode.SCHEDULE_ITEM_NOT_FOUND));
        return toContext(schedule.getGoalId(), item.getLearningTaskId(), item.getTitle(), item.getAllocatedMinutes());
    }

    private com.steadyteller.backend.schedule.entity.Schedule findOwnedSchedule(Long memberId, Long scheduleId) {
        var schedule = schedules.findById(scheduleId)
            .orElseThrow(() -> new CustomException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (!schedule.getMemberId().equals(memberId))
            throw new CustomException(ScheduleErrorCode.SCHEDULE_ACCESS_DENIED);
        return schedule;
    }

    private Context toContext(Long goalId, Long learningTaskId, String title, int plannedMinutes) {
        if (learningTaskId == null) throw new CustomException(TimerErrorCode.EMPTY_ITEM);
        return new Context(goalId, learningTaskId, title, plannedMinutes);
    }
}
