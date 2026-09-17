package com.steadyteller.backend.membergoal.service;

import com.steadyteller.backend.learningtask.dto.LearningTaskResponseDto;
import com.steadyteller.backend.learningtask.service.LearningTaskService;
import com.steadyteller.backend.member.domain.Availability;
import com.steadyteller.backend.member.dto.AvailabilityResponse;
import com.steadyteller.backend.member.repository.AvailabilityRepository;
import com.steadyteller.backend.member.service.AvailabilityService;
import com.steadyteller.backend.membergoal.dto.GoalPlanGenerationRequestDto;
import com.steadyteller.backend.membergoal.dto.GoalPlanGenerationResponseDto;
import com.steadyteller.backend.membergoal.dto.MemberGoalResponseDto;
import com.steadyteller.backend.membergoal.dto.MemberStudyInfoRequestDto;
import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;
import com.steadyteller.backend.schedule.service.ScheduleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates the only creation flow used by the time-based planner. */
@Service
@RequiredArgsConstructor
public class GoalPlanGenerationService {

    private final MemberGoalService memberGoalService;
    private final AvailabilityService availabilityService;
    private final AvailabilityRepository availabilityRepository;
    private final LearningTaskService learningTaskService;
    private final ScheduleService scheduleService;

    @Transactional
    public GoalPlanGenerationResponseDto generate(Long memberId, GoalPlanGenerationRequestDto request) {
        List<String> availableDays = request.availabilities().stream().filter(availabilityRequest -> availabilityRequest.isEnabledOrDefault())
                .map(availabilityRequest -> availabilityRequest.getDayOfWeek().name().substring(0, 3)).distinct().toList();
        MemberStudyInfoRequestDto goalRequest = new MemberStudyInfoRequestDto(
                request.goal().title(), request.goal().startDate(), request.goal().targetDate(), request.goal().currentLevel(),
                request.goal().dailyStudyHours(), availableDays, request.goal().focusArea(), request.goal().breakMinutes());
        MemberGoalResponseDto goal = memberGoalService.createGoal(memberId, goalRequest);

        // Availability is currently a member preference in the domain, so this endpoint replaces the preference atomically.
        availabilityRepository.deleteAll(availabilityRepository.findAllByMemberIdOrderByDayOfWeekAscStartTimeAsc(memberId));
        for (var availability : request.availabilities()) availabilityService.createAvailability(memberId, availability);
        List<Availability> savedWindows = availabilityRepository.findAllByMemberIdOrderByDayOfWeekAscStartTimeAsc(memberId);

        learningTaskService.generateTasksForAvailability(memberId, goal.id(), savedWindows);
        List<LearningTaskResponseDto> tasks = learningTaskService.confirmTasks(memberId, goal.id());
        ScheduleResponseDto schedule = scheduleService.generateSchedule(memberId, goal.id());
        return new GoalPlanGenerationResponseDto(goal,
                savedWindows.stream().map(AvailabilityResponse::from).toList(), tasks, schedule);
    }
}
