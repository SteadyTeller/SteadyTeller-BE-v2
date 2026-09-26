package com.steadyteller.backend.member.dto;

import java.time.DayOfWeek;
import java.util.List;

/** Weekdays that occur at least once during a goal's inclusive date range. */
public record EditableAvailabilityDaysResponse(List<DayOfWeek> days) {
}
