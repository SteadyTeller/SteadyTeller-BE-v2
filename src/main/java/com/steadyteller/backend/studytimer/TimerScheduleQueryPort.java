package com.steadyteller.backend.studytimer;
/** Reads and locks an owned execution item without changing scheduling state. */
public interface TimerScheduleQueryPort {
    Context findOwnedItem(Long memberId, Long scheduleId, Long itemId);
    Context findOwnedItemForUpdate(Long memberId, Long scheduleId, Long itemId);
    record Context(Long goalId, Long learningTaskId, String title, int plannedMinutes) {}
}
