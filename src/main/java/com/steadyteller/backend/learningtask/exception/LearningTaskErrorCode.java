package com.steadyteller.backend.learningtask.exception;

import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum LearningTaskErrorCode implements ErrorCode {
    CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "Task candidate was not found."),
    TASK_GENERATION_LOCKED(HttpStatus.CONFLICT, "T002", "Tasks have already been confirmed for this goal. Use the replan flow to create a new proposal."),
    AI_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "T003", "AI task generation failed."),
    PLAN_EXCEEDS_AVAILABLE_TIME(HttpStatus.BAD_REQUEST, "T004", "Generated learning work exceeds the available time before the target date."),
    CONFIRMED_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "T005", "Confirmed learning task was not found."),
    COMPLETED_TASK_CANNOT_BE_DELETED(HttpStatus.BAD_REQUEST, "T006", "A task with completed schedule items cannot be deleted.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
