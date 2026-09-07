package com.steadyteller.backend.member.dto;

import com.steadyteller.backend.member.domain.Availability;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AvailabilityResponse {

    private Long id;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private int availableMinutes;
    private boolean enabled;

    public static AvailabilityResponse from(Availability availability) {
        return AvailabilityResponse.builder()
                .id(availability.getId())
                .dayOfWeek(availability.getDayOfWeek())
                .startTime(availability.getStartTime())
                .endTime(availability.getEndTime())
                .availableMinutes(availability.getAvailableMinutes())
                .enabled(availability.isEnabled())
                .build();
    }
}
