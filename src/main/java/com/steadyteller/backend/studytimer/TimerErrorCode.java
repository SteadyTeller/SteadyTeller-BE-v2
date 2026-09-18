package com.steadyteller.backend.studytimer;
import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
@Getter
@AllArgsConstructor
public enum TimerErrorCode implements ErrorCode {
    INVALID_RESULT(HttpStatus.BAD_REQUEST, "T001", "학습 결과와 실패 이유를 확인해주세요."),
    ATTEMPT_CONFLICT(HttpStatus.CONFLICT, "T002", "이미 저장된 학습 시도와 요청 내용이 다릅니다."),
    EMPTY_ITEM(HttpStatus.BAD_REQUEST, "T003", "학습 항목이 배정되지 않은 보충 시간입니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
