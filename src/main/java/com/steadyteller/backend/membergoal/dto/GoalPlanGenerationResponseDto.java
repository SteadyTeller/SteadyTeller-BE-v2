package com.steadyteller.backend.membergoal.dto;

import com.steadyteller.backend.learningtask.dto.LearningTaskResponseDto;
import com.steadyteller.backend.member.dto.AvailabilityResponse;
import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;
import java.util.List;

public record GoalPlanGenerationResponseDto(
        MemberGoalResponseDto goal,
        List<AvailabilityResponse> availabilities,
        List<LearningTaskResponseDto> tasks,
        ScheduleResponseDto schedule
) {
}
