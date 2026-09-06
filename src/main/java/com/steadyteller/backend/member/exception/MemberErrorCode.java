package com.steadyteller.backend.member.exception;

import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "존재하지 않는 회원입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "M002", "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "M003", "이메일 또는 비밀번호가 일치하지 않습니다."),
    AVAILABILITY_NOT_FOUND(HttpStatus.NOT_FOUND, "M004", "학습 가능 시간을 찾을 수 없습니다."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "M005", "종료 시간은 시작 시간보다 늦어야 합니다."),
    AVAILABILITY_OVERLAP(HttpStatus.CONFLICT, "M006", "같은 요일에 겹치는 학습 가능 시간이 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
