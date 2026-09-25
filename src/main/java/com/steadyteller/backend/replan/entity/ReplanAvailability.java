package com.steadyteller.backend.replan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "replan_availability")
public class ReplanAvailability {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long proposalId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DayOfWeek dayOfWeek;
    @Column(nullable = false) private LocalTime startTime;
    @Column(nullable = false) private LocalTime endTime;
    @Column(nullable = false) private boolean enabled;

    private ReplanAvailability(Long proposalId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, boolean enabled) {
        this.proposalId = proposalId; this.dayOfWeek = dayOfWeek; this.startTime = startTime; this.endTime = endTime; this.enabled = enabled;
    }
    public static ReplanAvailability create(Long proposalId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, boolean enabled) {
        return new ReplanAvailability(proposalId, dayOfWeek, startTime, endTime, enabled);
    }
    public int getAvailableMinutes() {
        int minutes = (int) java.time.Duration.between(startTime, endTime).toMinutes();
        return endTime.equals(LocalTime.of(23, 59)) ? minutes + 1 : minutes;
    }
}
