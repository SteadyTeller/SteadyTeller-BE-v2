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

    @NotNull(message = "ê¸°ë³¸ íìµ ìì¤ì íììëë¤.")
    private LearningLevel defaultLevel;

    private LocalTime preferredStartTime;
    private boolean notificationEnabled;
}
