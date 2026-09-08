package com.steadyteller.backend.schedule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/**
 * 스케줄 항목의 날짜/시간대를 수동으로 재배치하는 요청 (CRUD의 Update).
 * allocatedMinutes를 생략하면 기존 값을 그대로 유지한다.
 */
public record ScheduleItemUpdateRequestDto(

        @NotNull
        LocalDate date,

        @Positive
        Integer allocatedMinutes
) {
}
