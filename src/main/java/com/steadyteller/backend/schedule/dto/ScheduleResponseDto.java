package com.steadyteller.backend.schedule.dto;

import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ScheduleResponseDto(
        Long scheduleId,
        Long goalId,
        LocalDate scheduleStartDate,
        LocalDate scheduleEndDate,
        List<DailyScheduleDto> dailySchedules
) {
    public static ScheduleResponseDto of(Schedule schedule, List<ScheduleItem> items) {
        // items는 이미 날짜 순으로 생성되었으므로 LinkedHashMap으로 삽입 순서(=날짜 순)를 그대로 보존한다.
        Map<LocalDate, List<ScheduleItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(ScheduleItem::getDate, LinkedHashMap::new, Collectors.toList()));

        List<DailyScheduleDto> dailySchedules = grouped.entrySet().stream()
                .map(entry -> DailyScheduleDto.of(entry.getKey(), entry.getValue()))
                .toList();

        return new ScheduleResponseDto(
                schedule.getId(),
                schedule.getGoalId(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                dailySchedules
        );
    }
}
