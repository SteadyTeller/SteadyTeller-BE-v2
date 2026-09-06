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
import jakarta.persistence.OneToOne;
import java.math.BigDecimal;
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
public class LearningProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LearningLevel defaultLevel;

    private LocalTime preferredStartTime;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal durationCorrectionRate;

    @Column(nullable = false)
    private boolean notificationEnabled;

    public static LearningProfile createDefault(Member member) {
        return LearningProfile.builder()
                .member(member)
                .defaultLevel(LearningLevel.BEGINNER)
                .durationCorrectionRate(BigDecimal.ONE)
                .notificationEnabled(true)
                .build();
    }

    public void update(
            LearningLevel defaultLevel,
            LocalTime preferredStartTime,
            boolean notificationEnabled
    ) {
        this.defaultLevel = defaultLevel;
        this.preferredStartTime = preferredStartTime;
        this.notificationEnabled = notificationEnabled;
    }

    public void updateDurationCorrectionRate(BigDecimal durationCorrectionRate) {
        this.durationCorrectionRate = durationCorrectionRate;
    }
}
