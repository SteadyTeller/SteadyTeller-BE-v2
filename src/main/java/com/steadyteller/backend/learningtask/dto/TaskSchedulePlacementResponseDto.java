package com.steadyteller.backend.learningtask.dto;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.entity.ScheduleItemStatus;
import java.time.*;
public record TaskSchedulePlacementResponseDto(Long scheduleId,Long scheduleItemId,LocalDate date,LocalTime startTime,LocalTime endTime,int allocatedMinutes,ScheduleItemStatus status){
 public static TaskSchedulePlacementResponseDto from(ScheduleItem i){return new TaskSchedulePlacementResponseDto(i.getSchedule().getId(),i.getId(),i.getDate(),i.getStartTime(),i.getEndTime(),i.getAllocatedMinutes(),i.getStatus());}
}
