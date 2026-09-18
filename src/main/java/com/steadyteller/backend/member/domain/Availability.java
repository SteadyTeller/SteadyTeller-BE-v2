package com.steadyteller.backend.member.domain;

import com.steadyteller.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Availability extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private int availableMinutes;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "member_goal_id", nullable = false)
    private Long memberGoalId;

    public static Availability create(
            Member member,
            Long memberGoalId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            boolean enabled
    ) {
        return Availability.builder()
                .member(member)
                .memberGoalId(memberGoalId)
                .dayOfWeek(dayOfWeek)
                .startTime(startTime)
                .endTime(endTime)
                .availableMinutes(calculateAvailableMinutes(startTime, endTime))
                .enabled(enabled)
                .build();
    }

    public void update(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, boolean enabled) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.availableMinutes = calculateAvailableMinutes(startTime, endTime);
        this.enabled = enabled;
    }

    /** 23:59 is the API-safe representation of the UI's inclusive 24:00 endpoint. */
    private static int calculateAvailableMinutes(LocalTime startTime, LocalTime endTime) {
        int minutes = (int) Duration.between(startTime, endTime).toMinutes();
        return endTime.equals(LocalTime.of(23, 59)) ? minutes + 1 : minutes;
    }
}
