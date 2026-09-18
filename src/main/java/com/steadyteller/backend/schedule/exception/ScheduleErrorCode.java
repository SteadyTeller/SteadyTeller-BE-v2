package com.steadyteller.backend.schedule.exception;

import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ScheduleErrorCode implements ErrorCode {
    NO_CONFIRMED_TASKS(HttpStatus.BAD_REQUEST, "S001", "No confirmed learning tasks exist."),
    INVALID_AVAILABLE_DAYS(HttpStatus.BAD_REQUEST, "S002", "No enabled availability exists for this goal."),
    SCHEDULE_GENERATION_FAILED(HttpStatus.BAD_REQUEST, "S003", "The schedule cannot be generated within the goal period."),
    INVALID_LEARNING_TASK_DURATION(HttpStatus.BAD_REQUEST, "S004", "A learning task has an invalid duration."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "S005", "Schedule not found."),
    SCHEDULE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "S006", "Schedule access denied."),
    SCHEDULE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "S007", "Schedule item not found."),
    INVALID_SCHEDULE_ITEM_MINUTES(HttpStatus.BAD_REQUEST, "S008", "Schedule item minutes are invalid."),
    SCHEDULE_ITEM_DATE_IN_PAST(HttpStatus.BAD_REQUEST, "S009", "A past date cannot be selected."),
    SCHEDULE_ITEM_DATE_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "S010", "The selected date is outside this goal's availability."),
    SCHEDULE_ITEM_CAPACITY_EXCEEDED(HttpStatus.BAD_REQUEST, "S011", "The selected date exceeds available study time."),
    SCHEDULE_ITEM_ALREADY_FINISHED(HttpStatus.BAD_REQUEST, "S013", "A completed schedule item cannot be moved.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
