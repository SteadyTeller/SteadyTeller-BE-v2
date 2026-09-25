package com.steadyteller.backend.learningtask.exception;
import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
@Getter @AllArgsConstructor
public enum LearningTaskErrorCode implements ErrorCode {
 CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND,"T001","Candidate not found."),
 AI_GENERATION_FAILED(HttpStatus.BAD_GATEWAY,"T003","AI task generation failed."),
 PLAN_EXCEEDS_AVAILABLE_TIME(HttpStatus.CONFLICT,"T004","Candidate tasks exceed available time."),
 CONFIRMED_TASK_NOT_FOUND(HttpStatus.NOT_FOUND,"T005","Confirmed task not found."),
 COMPLETED_TASK_CANNOT_BE_DELETED(HttpStatus.CONFLICT,"T006","Completed task cannot be deleted."),
 TASK_GENERATION_LOCKED(HttpStatus.CONFLICT,"T007","Tasks are already confirmed. Use replan.");
 private final HttpStatus status; private final String code; private final String message;
}
