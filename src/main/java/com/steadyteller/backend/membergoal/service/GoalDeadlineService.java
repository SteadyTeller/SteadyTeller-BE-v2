package com.steadyteller.backend.membergoal.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.membergoal.dto.GoalDeadlineResponse;
import com.steadyteller.backend.membergoal.dto.GoalContinuationRequest;
import com.steadyteller.backend.membergoal.dto.GoalContinuationResponse;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.exception.GoalErrorCode;
import com.steadyteller.backend.membergoal.port.GoalRemainingTaskQueryPort;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import com.steadyteller.backend.schedule.service.ScheduleService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalDeadlineService {
    private final MemberGoalRepository memberGoalRepository;
    private final GoalRemainingTaskQueryPort remainingTaskQueryPort;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleService scheduleService;

    public GoalDeadlineResponse getDeadline(Long memberId, Long goalId) {
        MemberGoal goal = memberGoalRepository.findById(goalId)
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) {
            throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
        }
        // 기존 목표일은 LocalDate이므로 한국 시간 해당 날짜가 끝난 뒤 도달로 판단한다.
        // A date-only deadline remains valid until the end of that calendar day.
        boolean reached = LocalDate.now(ZoneId.of("Asia/Seoul")).isAfter(goal.getTargetDate());
        return new GoalDeadlineResponse(goalId, goal.getTargetDate(), reached,
                remainingTaskQueryPort.findRemainingTasks(goalId));
    }

    @Transactional
    public GoalContinuationResponse continueGoal(Long memberId, Long goalId, GoalContinuationRequest request) {
        MemberGoal goal = memberGoalRepository.findByIdForUpdate(goalId)
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
        var remaining = remainingTaskQueryPort.findRemainingTasks(goalId);
        goal.extendTargetDate(request.newTargetDate());
        var schedule = scheduleRepository.findByGoalIdOrderByStartDateDesc(goalId).stream().findFirst()
                .map(value -> scheduleService.replanRemaining(memberId, value.getId())).orElse(null);
        return new GoalContinuationResponse(goalId, goal.getTargetDate(), remaining.size(), schedule);
    }
}
