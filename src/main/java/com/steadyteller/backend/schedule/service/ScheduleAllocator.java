package com.steadyteller.backend.schedule.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.schedule.exception.ScheduleErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Deterministically assigns one goal's tasks to that goal's available weekdays. */
@Component
public class ScheduleAllocator {
    public record AllocatedItem(LearningTask task, LocalDate date, int allocatedMinutes, int orderInDay) { }

    public List<AllocatedItem> allocate(List<LearningTask> tasks, LocalDate start, Set<DayOfWeek> availableDays,
                                        int capacity, int maxDays) {
        List<AllocatedItem> allocations = new ArrayList<>();
        Iterator<LearningTask> iterator = tasks.iterator();
        LearningTask pending = iterator.hasNext() ? iterator.next() : null;
        LocalDate date = start;
        int walked = 0;
        while (pending != null) {
            if (walked++ > maxDays) {
                throw new CustomException(ScheduleErrorCode.SCHEDULE_GENERATION_FAILED);
            }
            if (!availableDays.contains(date.getDayOfWeek())) {
                date = date.plusDays(1);
                continue;
            }
            int remaining = capacity;
            int order = 1;
            boolean placed = false;
            while (pending != null && (!placed || pending.getAllocatedMinutes() <= remaining)) {
                allocations.add(new AllocatedItem(pending, date, pending.getAllocatedMinutes(), order++));
                remaining -= pending.getAllocatedMinutes();
                placed = true;
                pending = iterator.hasNext() ? iterator.next() : null;
            }
            date = date.plusDays(1);
        }
        return allocations;
    }
}
