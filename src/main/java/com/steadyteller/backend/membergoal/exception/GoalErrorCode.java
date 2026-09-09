package com.steadyteller.backend.membergoal.exception;

import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GoalErrorCode implements ErrorCode {

    GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, "G001", "존재하지 않는 학습 목표입니다."),
    GOAL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "G002", "해당 학습 목표에 대한 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
