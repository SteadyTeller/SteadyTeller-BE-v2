package com.steadyteller.backend.schedule.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.schedule.exception.ScheduleErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
import com.steadyteller.backend.member.domain.Availability;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Deterministically assigns one goal's tasks to that goal's available weekdays. */
@Component
public class ScheduleAllocator {
    public record TimeSlotAllocation(LearningTask task, LocalDate date, int allocatedMinutes,
                                     int orderInDay, LocalTime startTime, LocalTime endTime) { }

    /** Splits tasks over every concrete availability window, never past the goal deadline. */
    public List<TimeSlotAllocation> allocateByWindows(List<LearningTask> tasks, LocalDate start, LocalDate end,
                                                       List<Availability> availabilities) {
        List<TimeSlotAllocation> allocations = new ArrayList<>();
        if (start.isAfter(end)) {
            throw new CustomException(ScheduleErrorCode.SCHEDULE_GENERATION_FAILED);
        }
        int taskIndex = 0;
        int remainingForTask = tasks.isEmpty() ? 0 : tasks.getFirst().getAllocatedMinutes();
        LocalDate date = start;
        while (!date.isAfter(end) && taskIndex < tasks.size()) {
            int order = 1;
            for (Availability availability : availabilities) {
                if (!availability.isEnabled() || availability.getDayOfWeek() != date.getDayOfWeek()) continue;
                LocalTime cursor = availability.getStartTime();
                int windowRemaining = availability.getAvailableMinutes();
                while (windowRemaining > 0 && taskIndex < tasks.size()) {
                    int minutes = Math.min(remainingForTask, windowRemaining);
                    LearningTask task = tasks.get(taskIndex);
                    allocations.add(new TimeSlotAllocation(task, date, minutes, order++, cursor,
                            cursor.plusMinutes(minutes)));
                    cursor = cursor.plusMinutes(minutes);
                    windowRemaining -= minutes;
                    remainingForTask -= minutes;
                    if (remainingForTask == 0) {
                        taskIndex++;
                        if (taskIndex < tasks.size()) remainingForTask = tasks.get(taskIndex).getAllocatedMinutes();
                    }
                }
            }
            date = date.plusDays(1);
        }
        if (taskIndex < tasks.size()) {
            throw new CustomException(ScheduleErrorCode.SCHEDULE_GENERATION_FAILED);
        }
        return allocations;
    }

}
