package com.steadyteller.backend.membergoal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/**
 * 학습 목표 설정 요청 DTO (설계 명세 1번). memberId는 body가 아닌 JWT에서 추출한다.
 * 생성(POST)과 수정(PATCH) 모두 동일한 필드 구성을 사용한다.
 */
public record MemberStudyInfoRequestDto(

        @NotBlank
        String title,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate targetDate,

        @NotBlank
        String currentLevel,

        @NotNull
        @Positive
        Integer dailyStudyHours,

        @NotEmpty
        List<String> availableDays,

        @NotBlank
        String focusArea
) {
}
