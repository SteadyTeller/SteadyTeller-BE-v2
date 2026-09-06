package com.steadyteller.backend.member.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
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
public class AvailabilityRequest {

    @NotNull(message = "ìì¼ì íììëë¤.")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "ìì ìê°ì íììëë¤.")
    private LocalTime startTime;

    @NotNull(message = "ì¢ë£ ìê°ì íììëë¤.")
    private LocalTime endTime;

    private Boolean enabled;

    public boolean isEnabledOrDefault() {
        return enabled == null || enabled;
    }
}
