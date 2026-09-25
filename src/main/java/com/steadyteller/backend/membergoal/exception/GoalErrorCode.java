package com.steadyteller.backend.membergoal.exception;

import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GoalErrorCode implements ErrorCode {
    GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, "G001", "존재하지 않는 학습 목표입니다."),
    GOAL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "G002", "해당 학습 목표에 대한 권한이 없습니다."),
    GOAL_ALREADY_PLANNED(HttpStatus.CONFLICT, "G003", "태스크가 확정된 목표는 재계획으로만 변경할 수 있습니다."),
    REPLAN_NOT_AVAILABLE(HttpStatus.CONFLICT, "G004", "재계획할 미완료 학습이 없습니다."),
    REPLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "G005", "작성 중인 재계획안을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
