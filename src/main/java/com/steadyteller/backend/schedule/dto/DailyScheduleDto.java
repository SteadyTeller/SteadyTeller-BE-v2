package com.steadyteller.backend.schedule.dto;

import com.steadyteller.backend.schedule.entity.ScheduleItem;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public record DailyScheduleDto(
        LocalDate date,
        DayOfWeek dayOfWeek,
        int totalAllocatedMinutes,
        List<ScheduleItemResponseDto> items
) {
    public static DailyScheduleDto of(LocalDate date, List<ScheduleItem> itemsOnDate) {
        int totalAllocatedMinutes = itemsOnDate.stream().mapToInt(ScheduleItem::getAllocatedMinutes).sum();
        List<ScheduleItemResponseDto> items = itemsOnDate.stream().map(ScheduleItemResponseDto::from).toList();
        return new DailyScheduleDto(date, itemsOnDate.get(0).getDayOfWeek(), totalAllocatedMinutes, items);
    }
}
