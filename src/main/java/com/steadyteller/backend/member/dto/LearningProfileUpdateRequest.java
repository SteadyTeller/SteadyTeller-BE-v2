package com.steadyteller.backend.member.dto;

import com.steadyteller.backend.member.domain.LearningLevel;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import lombok.Builder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LearningProfileUpdateRequest {

    @NotNull(message = "기본 학습 수준은 필수입니다.")
    private LearningLevel defaultLevel;

    private LocalTime preferredStartTime;
    private boolean notificationEnabled;
}
