package com.steadyteller.backend.statistics.exception;

import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum StatisticsErrorCode implements ErrorCode {

    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "ST001", "시작일은 종료일보다 늦을 수 없습니다."),
    DATA_SOURCE_NOT_CONNECTED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "ST002",
            "스케줄 및 수행 기록 데이터 연결이 아직 완료되지 않았습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
