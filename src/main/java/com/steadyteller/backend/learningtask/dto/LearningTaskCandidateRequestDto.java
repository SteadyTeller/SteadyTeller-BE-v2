package com.steadyteller.backend.learningtask.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 후보 태스크 생성(사용자 직접 추가)/수정 요청 DTO.
 */
public record LearningTaskCandidateRequestDto(

        @NotBlank
        String title,

        @NotBlank
        String category,

        @NotBlank
        String subject,

        @NotNull
        @Min(1)
        @Max(5)
        Integer difficulty,

        @NotNull
        @Positive
        Integer allocatedMinutes
) {
}
