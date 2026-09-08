package com.steadyteller.backend.schedule.exception;

import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ScheduleErrorCode implements ErrorCode {

    NO_CONFIRMED_TASKS(HttpStatus.BAD_REQUEST, "S001", "스케줄을 생성할 확정된 학습 태스크가 없습니다."),
    INVALID_AVAILABLE_DAYS(HttpStatus.BAD_REQUEST, "S002", "가용 요일 값이 올바르지 않습니다."),
    SCHEDULE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S003", "스케줄 생성에 실패했습니다."),
    INVALID_LEARNING_TASK_DURATION(HttpStatus.BAD_REQUEST, "S004", "예상 소요 시간이 올바르지 않은 학습 태스크가 있습니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "S005", "존재하지 않는 스케줄입니다."),
    SCHEDULE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "S006", "해당 스케줄에 대한 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
