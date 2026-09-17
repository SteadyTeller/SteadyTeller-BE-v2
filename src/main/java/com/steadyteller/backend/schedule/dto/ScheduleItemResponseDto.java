package com.steadyteller.backend.schedule.dto;

import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.entity.ScheduleItemStatus;
import com.steadyteller.backend.schedule.entity.ScheduleItemKind;
import java.time.LocalTime;

public record ScheduleItemResponseDto(
        Long scheduleItemId,
        Long learningTaskId,
        String title,
        int allocatedMinutes,
        int order,
        ScheduleItemStatus status,
        ScheduleItemKind kind,
        LocalTime startTime,
        LocalTime endTime
) {
    public static ScheduleItemResponseDto from(ScheduleItem item) {
        return new ScheduleItemResponseDto(
                item.getId(),
                item.getLearningTaskId(),
                item.getTitle(),
                item.getAllocatedMinutes(),
                item.getOrderIndex(),
                item.getStatus(),
                item.getKind(),
                item.getStartTime(),
                item.getEndTime()
        );
    }
}
