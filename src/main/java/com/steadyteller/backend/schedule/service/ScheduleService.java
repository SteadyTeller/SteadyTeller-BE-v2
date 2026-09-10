package com.steadyteller.backend.schedule.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskStatus;
import com.steadyteller.backend.learningtask.repository.LearningTaskRepository;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.exception.GoalErrorCode;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.schedule.dto.ScheduleItemUpdateRequestDto;
import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;
import com.steadyteller.backend.schedule.dto.ScheduleSummaryDto;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.exception.ScheduleErrorCode;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MemberGoal + 확정된 LearningTask 목록을 바탕으로 일자별 학습 스케줄을 생성/조회한다 (설계 명세 4번).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    // 무한 루프 방지용 안전장치. availableDays 파싱은 이미 사전에 검증하므로 정상 흐름에서는 도달하지 않는다.
    private static final int MAX_HORIZON_DAYS = 3650;
    public static final int MAX_ALLOCATED_MINUTES = 1440;

    private final MemberGoalRepository memberGoalRepository;
    private final LearningTaskRepository learningTaskRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final ScheduleAiService scheduleAiService;

    @Transactional
    public ScheduleResponseDto generateSchedule(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoal(memberId, goalId);
        List<LearningTask> confirmedTasks =
                learningTaskRepository.findByGoalIdAndStatusForUpdate(goalId, LearningTaskStatus.PENDING);
        if (confirmedTasks.isEmpty()) {
            throw new CustomException(ScheduleErrorCode.NO_CONFIRMED_TASKS);
        }
        validateAllocatedMinutes(confirmedTasks);

        Set<DayOfWeek> availableDays = parseAvailableDays(goal.getAvailableDays());
        int dailyCapacityMinutes = calculateDailyCapacityMinutes(goal);
        LocalDate earliestStart = earliestStart(goal);

        List<ScheduleAllocator.AllocatedItem> allocations = scheduleAiService.generateSchedule(
                goal, confirmedTasks, earliestStart, availableDays, dailyCapacityMinutes, MAX_HORIZON_DAYS
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

        for (LearningTask task : confirmedTasks) {
            task.markAsScheduled();
        }

        return ScheduleResponseDto.of(schedule, items);
    }

    public ScheduleResponseDto getSchedule(Long memberId, Long scheduleId) {
        Schedule schedule = getOwnedSchedule(memberId, scheduleId);
        List<ScheduleItem> items = scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(scheduleId);
        return ScheduleResponseDto.of(schedule, items);
    }

    public List<ScheduleSummaryDto> listSchedules(Long memberId, Long goalId) {
        getOwnedGoal(memberId, goalId);
        return scheduleRepository.findByGoalIdOrderByStartDateDesc(goalId).stream()
                .map(schedule -> ScheduleSummaryDto.of(
                        schedule, scheduleItemRepository.countByScheduleId(schedule.getId())
                ))
                .toList();
    }

    @Transactional
    public void deleteSchedule(Long memberId, Long scheduleId) {
        Schedule schedule = getOwnedSchedule(memberId, scheduleId);
        List<ScheduleItem> items = scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(scheduleId);
        List<Long> taskIds = items.stream().map(ScheduleItem::getLearningTaskId).toList();
        if (!taskIds.isEmpty()) {
            List<LearningTask> tasks = learningTaskRepository.findAllById(taskIds);
            for (LearningTask task : tasks) {
                if (task.getStatus() == LearningTaskStatus.SCHEDULED) {
                    task.markAsPending();
                }
            }
        }
        scheduleItemRepository.deleteAll(items);
        scheduleItemRepository.flush();
        scheduleRepository.delete(schedule);
    }

    /**
     * 스케줄 항목 하나의 수행 날짜/시간대를 수동으로 재배치한다 (CRUD의 Update).
     * status(학습 수행 상태) 변경은 별도 단계(학습 수행) 소관이라 여기서 다루지 않는다.
     */
    @Transactional
    public ScheduleResponseDto updateScheduleItem(
            Long memberId, Long scheduleId, Long itemId, ScheduleItemUpdateRequestDto request
    ) {
        Schedule schedule = getOwnedSchedule(memberId, scheduleId);
        MemberGoal goal = memberGoalRepository.findById(schedule.getGoalId())
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));

        List<ScheduleItem> items = scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(scheduleId);
        ScheduleItem target = items.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ScheduleErrorCode.SCHEDULE_ITEM_NOT_FOUND));

        LocalDate newDate = request.date();
        int newMinutes = request.allocatedMinutes() != null ? request.allocatedMinutes() : target.getAllocatedMinutes();
        if (newMinutes <= 0) {
            throw new CustomException(ScheduleErrorCode.INVALID_SCHEDULE_ITEM_MINUTES);
        }
        if (newDate.isBefore(LocalDate.now())) {
            throw new CustomException(ScheduleErrorCode.SCHEDULE_ITEM_DATE_IN_PAST);
        }
        Set<DayOfWeek> availableDays = parseAvailableDays(goal.getAvailableDays());
        if (!availableDays.contains(newDate.getDayOfWeek())) {
            throw new CustomException(ScheduleErrorCode.SCHEDULE_ITEM_DATE_NOT_AVAILABLE);
        }

        LocalDate oldDate = target.getDate();
        List<ScheduleItem> othersOnNewDate = items.stream()
                .filter(item -> !item.getId().equals(itemId) && item.getDate().equals(newDate))
                .toList();
        int existingMinutesOnNewDate = othersOnNewDate.stream().mapToInt(ScheduleItem::getAllocatedMinutes).sum();
        int dailyCapacityMinutes = calculateDailyCapacityMinutes(goal);
        // 그 날짜의 유일한 항목이 되는 경우는 하루 한도를 넘어도 허용한다 (ScheduleAllocator와 동일한 예외 규칙).
        if (!othersOnNewDate.isEmpty() && existingMinutesOnNewDate + newMinutes > dailyCapacityMinutes) {
            throw new CustomException(ScheduleErrorCode.SCHEDULE_ITEM_CAPACITY_EXCEEDED);
        }

        target.reschedule(newDate, newDate.getDayOfWeek(), newMinutes);
        if (!oldDate.equals(newDate)) {
            renumberOrderForDate(items, oldDate, null);
            renumberOrderForDate(items, newDate, target);
        }

        return ScheduleResponseDto.of(schedule, items);
    }

    private void renumberOrderForDate(List<ScheduleItem> allItems, LocalDate date, ScheduleItem appendLast) {
        List<ScheduleItem> onDate = allItems.stream()
                .filter(item -> item.getDate().equals(date)
                        && (appendLast == null || !item.getId().equals(appendLast.getId())))
                .sorted(Comparator.comparingInt(ScheduleItem::getOrderIndex))
                .collect(Collectors.toCollection(ArrayList::new));
        if (appendLast != null) {
            onDate.add(appendLast);
        }
        int order = 1;
        for (ScheduleItem item : onDate) {
            item.updateOrderIndex(order++);
        }
    }

    private Schedule getOwnedSchedule(Long memberId, Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new CustomException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (!schedule.getMemberId().equals(memberId)) {
            throw new CustomException(ScheduleErrorCode.SCHEDULE_ACCESS_DENIED);
        }
        return schedule;
    }

    private MemberGoal getOwnedGoal(Long memberId, Long goalId) {
        MemberGoal goal = memberGoalRepository.findById(goalId)
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) {
            throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
        }
        return goal;
    }

    private void validateAllocatedMinutes(List<LearningTask> tasks) {
        // LearningTask.allocatedMinutes는 AI 생성 시점(#1/PR #6)에 양수 및 상한(MAX_ALLOCATED_MINUTES)으로 검증되지만,
        // 검토 단계에서 사용자가 후보를 직접 수정/추가(PATCH, POST)할 때는 그 검증을 다시 거치지 않을 수 있다.
        // 0 이하 값이나 비정상적으로 큰 값이 섞여 들어오면 배정 알고리즘이 하루에 태스크를 무한정 몰아넣거나
        // 스케줄 기간이 왜곡되므로 스케줄링 시점에도 한 번 더 방어적으로 검증한다.
        boolean hasInvalidDuration = tasks.stream().anyMatch(task ->
                task.getAllocatedMinutes() <= 0 || task.getAllocatedMinutes() > MAX_ALLOCATED_MINUTES);
        if (hasInvalidDuration) {
            throw new CustomException(ScheduleErrorCode.INVALID_LEARNING_TASK_DURATION);
        }
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

    private int calculateDailyCapacityMinutes(MemberGoal goal) {
        if (goal.getDailyStudyHours() == null || goal.getDailyStudyHours() <= 0) {
            throw new CustomException(ScheduleErrorCode.INVALID_DAILY_STUDY_HOURS);
        }
        return goal.getDailyStudyHours() * 60;
    }
}
