package com.steadyteller.backend.membergoal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record MemberStudyInfoRequestDto(
        @NotBlank String title,
        @NotNull LocalDate startDate,
        @NotNull LocalDate targetDate,
        @NotBlank String currentLevel,
        @NotEmpty List<@NotBlank @Size(max = 200) String> mustStudyTopics
) { }
