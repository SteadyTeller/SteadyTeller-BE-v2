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
    public record AllocationPlan(List<AllocatedItem> regularItems, List<LocalDate> supplementDates) { }

    public AllocationPlan allocateWithSupplementDays(List<LearningTask> tasks, LocalDate start,
            Set<DayOfWeek> availableDays, int capacity, int supplementEvery, int maxDays) {
        if (supplementEvery <= 0) throw new IllegalArgumentException("supplementEvery must be positive");
        List<AllocatedItem> regular = new ArrayList<>();
        List<LocalDate> supplements = new ArrayList<>();
        Iterator<LearningTask> iterator = tasks.iterator();
        LearningTask pending = iterator.hasNext() ? iterator.next() : null;
        LocalDate date = start;
        int walked = 0;
        int regularDays = 0;
        while (pending != null) {
            if (walked++ > maxDays) throw new CustomException(ScheduleErrorCode.SCHEDULE_GENERATION_FAILED);
            if (!availableDays.contains(date.getDayOfWeek())) { date = date.plusDays(1); continue; }
            if (regularDays == supplementEvery) {
                supplements.add(date); regularDays = 0; date = date.plusDays(1); continue;
            }
            int remaining = capacity;
            int order = 1;
            boolean placed = false;
            while (pending != null && (!placed || pending.getAllocatedMinutes() <= remaining)) {
                regular.add(new AllocatedItem(pending, date, pending.getAllocatedMinutes(), order++));
                remaining -= pending.getAllocatedMinutes();
                placed = true;
                pending = iterator.hasNext() ? iterator.next() : null;
            }
            regularDays++;
            date = date.plusDays(1);
        }
        while (regularDays > 0 && walked++ <= maxDays) {
            if (availableDays.contains(date.getDayOfWeek())) { supplements.add(date); break; }
            date = date.plusDays(1);
        }
        return new AllocationPlan(regular, supplements);
    }

    public List<AllocatedItem> allocate(List<LearningTask> tasks, LocalDate start, Set<DayOfWeek> availableDays,
            int capacity, int maxDays) {
        return allocateWithSupplementDays(tasks, start, availableDays, capacity, Integer.MAX_VALUE, maxDays).regularItems();
    }
}
