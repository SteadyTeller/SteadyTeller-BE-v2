package com.steadyteller.backend.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GlobalErrorCode implements ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "Invalid Input Value"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "Invalid HTTP Method"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C003", "Server Error"),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C004", "Invalid Type Value"),
    
    // Auth (Security / JWT Filter Level)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "Unauthorized Request"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "Invalid JWT Token");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
