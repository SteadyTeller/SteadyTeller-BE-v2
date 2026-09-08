package com.steadyteller.backend.schedule.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskStatus;
import com.steadyteller.backend.learningtask.repository.LearningTaskRepository;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.exception.GoalErrorCode;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.exception.ScheduleErrorCode;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MemberGoal + 확정된 LearningTask 목록을 바탕으로 일자별 학습 스케줄을 생성한다 (설계 명세 4번).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    // 무한 루프 방지용 안전장치. availableDays 파싱은 이미 사전에 검증하므로 정상 흐름에서는 도달하지 않는다.
    private static final int MAX_HORIZON_DAYS = 3650;

    private final MemberGoalRepository memberGoalRepository;
    private final LearningTaskRepository learningTaskRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final ScheduleAiService scheduleAiService;
    private final ScheduleAllocator scheduleAllocator;

    @Transactional
    public ScheduleResponseDto generateSchedule(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoal(memberId, goalId);
        List<LearningTask> confirmedTasks =
                learningTaskRepository.findByGoalIdAndStatus(goalId, LearningTaskStatus.PENDING);
        if (confirmedTasks.isEmpty()) {
            throw new CustomException(ScheduleErrorCode.NO_CONFIRMED_TASKS);
        }

        Set<DayOfWeek> availableDays = parseAvailableDays(goal.getAvailableDays());
        int dailyCapacityMinutes = goal.getDailyStudyHours() * 60;
        LocalDate earliestStart = earliestStart(goal);

        List<LearningTask> orderedTasks = scheduleAiService.orderTasks(goal, confirmedTasks);
        List<ScheduleAllocator.AllocatedItem> allocations = scheduleAllocator.allocate(
                orderedTasks, earliestStart, availableDays, dailyCapacityMinutes, MAX_HORIZON_DAYS
        );

        Schedule schedule = scheduleRepository.save(Schedule.create(
                memberId,
                goalId,
                allocations.get(0).date(),
                allocations.get(allocations.size() - 1).date()
        ));

        List<ScheduleItem> items = allocations.stream()
                .map(allocation -> ScheduleItem.create(
                        schedule,
                        allocation.task().getId(),
                        allocation.task().getTitle(),
                        allocation.date(),
                        allocation.date().getDayOfWeek(),
                        allocation.allocatedMinutes(),
                        allocation.orderInDay()
                ))
                .toList();
        scheduleItemRepository.saveAll(items);

        return ScheduleResponseDto.of(schedule, items);
    }

    private MemberGoal getOwnedGoal(Long memberId, Long goalId) {
        MemberGoal goal = memberGoalRepository.findById(goalId)
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) {
            throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
        }
        return goal;
    }

    private LocalDate earliestStart(MemberGoal goal) {
        LocalDate today = LocalDate.now();
        return goal.getStartDate().isAfter(today) ? goal.getStartDate() : today;
    }

    private Set<DayOfWeek> parseAvailableDays(List<String> availableDays) {
        if (availableDays == null || availableDays.isEmpty()) {
            throw new CustomException(ScheduleErrorCode.INVALID_AVAILABLE_DAYS);
        }
        Set<DayOfWeek> parsed = new LinkedHashSet<>();
        for (String raw : availableDays) {
            parsed.add(toDayOfWeek(raw));
        }
        return parsed;
    }

    private DayOfWeek toDayOfWeek(String raw) {
        if (raw == null) {
            throw new CustomException(ScheduleErrorCode.INVALID_AVAILABLE_DAYS);
        }
        return switch (raw.trim().toUpperCase()) {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN" -> DayOfWeek.SUNDAY;
            default -> throw new CustomException(ScheduleErrorCode.INVALID_AVAILABLE_DAYS);
        };
    }
}
