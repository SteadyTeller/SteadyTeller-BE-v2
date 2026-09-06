package com.steadyteller.backend.member.dto;

import com.steadyteller.backend.member.domain.LearningLevel;
import com.steadyteller.backend.member.domain.LearningProfile;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LearningProfileResponse {

    private Long memberId;
    private LearningLevel defaultLevel;
    private LocalTime preferredStartTime;
    private BigDecimal durationCorrectionRate;
    private boolean notificationEnabled;

    public static LearningProfileResponse from(LearningProfile profile) {
        return LearningProfileResponse.builder()
                .memberId(profile.getMember().getId())
                .defaultLevel(profile.getDefaultLevel())
                .preferredStartTime(profile.getPreferredStartTime())
                .durationCorrectionRate(profile.getDurationCorrectionRate())
                .notificationEnabled(profile.isNotificationEnabled())
                .build();
    }
}
