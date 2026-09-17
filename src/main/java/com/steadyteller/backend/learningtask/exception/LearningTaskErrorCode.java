package com.steadyteller.backend.learningtask.exception;

import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum LearningTaskErrorCode implements ErrorCode {
    CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "Task candidate was not found."),
    CANDIDATE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "T002", "You do not have access to this task candidate."),
    AI_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "T003", "AI task generation failed."),
    PLAN_EXCEEDS_AVAILABLE_TIME(HttpStatus.BAD_REQUEST, "T004", "Generated learning work exceeds the available time before the target date.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
