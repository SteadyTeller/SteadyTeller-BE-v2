package com.steadyteller.backend.learningtask.exception;

import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum LearningTaskErrorCode implements ErrorCode {

    CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "존재하지 않거나 만료된 후보 태스크입니다."),
    CANDIDATE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "T002", "해당 후보 태스크에 대한 권한이 없습니다."),
    AI_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "T003", "AI 태스크 생성에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
