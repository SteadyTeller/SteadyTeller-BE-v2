package com.steadyteller.backend.schedule.dto;

import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.entity.ScheduleItemStatus;

public record ScheduleItemResponseDto(
        Long scheduleItemId,
        Long learningTaskId,
        String title,
        int allocatedMinutes,
        int order,
        ScheduleItemStatus status
) {
    public static ScheduleItemResponseDto from(ScheduleItem item) {
        return new ScheduleItemResponseDto(
                item.getId(),
                item.getLearningTaskId(),
                item.getTitle(),
                item.getAllocatedMinutes(),
                item.getOrderIndex(),
                item.getStatus()
        );
    }
}
