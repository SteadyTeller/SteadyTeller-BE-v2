package com.steadyteller.backend.membergoal.dto;

import com.steadyteller.backend.membergoal.entity.MemberGoal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MemberGoalResponseDto(
        Long id,
        Long memberId,
        String title,
        LocalDate startDate,
        LocalDate targetDate,
        String currentLevel,
        Integer dailyStudyHours,
        List<String> availableDays,
        String focusArea,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MemberGoalResponseDto from(MemberGoal goal) {
        return new MemberGoalResponseDto(
                goal.getId(),
                goal.getMemberId(),
                goal.getTitle(),
                goal.getStartDate(),
                goal.getTargetDate(),
                goal.getCurrentLevel(),
                goal.getDailyStudyHours(),
                goal.getAvailableDays(),
                goal.getFocusArea(),
                goal.getCreatedAt(),
                goal.getUpdatedAt()
        );
    }
}
