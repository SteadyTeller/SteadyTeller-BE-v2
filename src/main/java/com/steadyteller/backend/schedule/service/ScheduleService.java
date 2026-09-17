package com.steadyteller.backend.schedule.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskStatus;
import com.steadyteller.backend.learningtask.repository.LearningTaskRepository;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.member.domain.Availability;
import com.steadyteller.backend.member.repository.AvailabilityRepository;
import com.steadyteller.backend.membergoal.exception.GoalErrorCode;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.schedule.dto.ScheduleItemResponseDto;
import com.steadyteller.backend.schedule.dto.ScheduleItemFailureRequestDto;
import com.steadyteller.backend.schedule.dto.ScheduleFailureResultDto;
import com.steadyteller.backend.schedule.dto.ScheduleItemUpdateRequestDto;
import com.steadyteller.backend.schedule.dto.ScheduleResponseDto;
import com.steadyteller.backend.schedule.dto.ScheduleSummaryDto;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.entity.ScheduleItemStatus;
import com.steadyteller.backend.schedule.entity.ScheduleItemKind;
import com.steadyteller.backend.schedule.entity.ScheduleFailure;
import com.steadyteller.backend.schedule.entity.FailureHandlingAction;
import com.steadyteller.backend.schedule.exception.ScheduleErrorCode;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import com.steadyteller.backend.schedule.repository.ScheduleFailureRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * MemberGoal + 확정된 LearningTask 목록을 바탕으로 일자별 학습 스케줄을 생성/조회한다 (설계 명세 4번).
 * <p>
 * 스케줄 생성(generateSchedule)은 {@link #getOwnedGoalForUpdate}로 MemberGoal 로우에 비관적 락을 먼저 잡고 실행되어,
 * 목표 삭제(deleteGoal) 및 태스크 확정(confirmTasks)과의 동시성 경합 시 고아 데이터 발생 및 참조 무결성 파괴를 원천 방지한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Transactional(readOnly = true)
public class ScheduleService {

    // 무한 루프 방지용 안전장치. availableDays 파싱은 이미 사전에 검증하므로 정상 흐름에서는 도달하지 않는다.
    private static final int MAX_HORIZON_DAYS = 3650;
    public static final int MAX_ALLOCATED_MINUTES = 1440;

    private final MemberGoalRepository memberGoalRepository;
    private final LearningTaskRepository learningTaskRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final ScheduleFailureRepository scheduleFailureRepository;
    private final ScheduleAiService scheduleAiService;

    @Autowired(required = false)
    private AvailabilityRepository availabilityRepository;

    /** Kept for source compatibility with callers built before failure history was introduced. */
    public ScheduleService(MemberGoalRepository memberGoalRepository, LearningTaskRepository learningTaskRepository,
                           ScheduleRepository scheduleRepository, ScheduleItemRepository scheduleItemRepository,
                           ScheduleAiService scheduleAiService) {
        this.memberGoalRepository = memberGoalRepository;
        this.learningTaskRepository = learningTaskRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.scheduleFailureRepository = null;
        this.scheduleAiService = scheduleAiService;
    }

    @Transactional
    public ScheduleResponseDto generateSchedule(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoalForUpdate(memberId, goalId);
        log.info("Schedule generation started: memberId={}, goalId={}, title={}", memberId, goalId, goal.getTitle());
        List<LearningTask> confirmedTasks =
                learningTaskRepository.findByGoalIdAndStatusForUpdate(goalId, LearningTaskStatus.PENDING);
        if (confirmedTasks.isEmpty()) {
            log.warn("Schedule generation rejected: no confirmed pending tasks. goalId={}", goalId);
            throw new CustomException(ScheduleErrorCode.NO_CONFIRMED_TASKS);
        }
        validateAllocatedMinutes(confirmedTasks);

        Set<DayOfWeek> availableDays = resolveAvailableDays(memberId, goal);
        int dailyCapacityMinutes = calculateDailyCapacityMinutes(memberId, goal, availableDays);
        LocalDate earliestStart = earliestStart(goal);

        List<ScheduleAllocator.AllocatedItem> allocations = scheduleAiService.generateSchedule(
                goal, confirmedTasks, earliestStart, availableDays, dailyCapacityMinutes, MAX_HORIZON_DAYS
        );
        log.info("Schedule allocation completed: goalId={}, taskCount={}, allocationCount={}, dailyCapacityMinutes={}",
                goalId, confirmedTasks.size(), allocations.size(), dailyCapacityMinutes);

        ScheduleAllocator.AllocationPlan plan = scheduleFailureRepository == null ? null
                : new ScheduleAllocator().allocateWithSupplementDays(allocations.stream()
                        .map(ScheduleAllocator.AllocatedItem::task).toList(), earliestStart, availableDays,
                        dailyCapacityMinutes, supplementEveryRegularDays(dailyCapacityMinutes), MAX_HORIZON_DAYS);
        List<ScheduleAllocator.AllocatedItem> plannedAllocations = plan == null ? allocations : plan.regularItems();
        Schedule schedule = scheduleRepository.save(Schedule.create(
                memberId,
                goalId,
                plannedAllocations.getFirst().date(),
                plannedAllocations.getLast().date()
        ));

        // The compatibility constructor is retained for pre-failure-history callers/tests only.
        List<ScheduleItem> items = scheduleFailureRepository == null
                ? createRegularItems(schedule, allocations)
                : createItemsWithSupplementDays(schedule, plan, dailyCapacityMinutes);
        if (scheduleFailureRepository != null) {
            insertBreaksAndAssignTimeRanges(memberId, items, goal.getBreakMinutes());
        }
        scheduleItemRepository.saveAll(items);
        schedule.updatePeriod(schedule.getStartDate(), items.stream().map(ScheduleItem::getDate)
                .max(LocalDate::compareTo).orElse(schedule.getEndDate()));

        for (LearningTask task : confirmedTasks) {
            task.markAsScheduled();
        }

        log.info("Schedule generation completed: scheduleId={}, goalId={}, itemCount={}, startDate={}, endDate={}",
                schedule.getId(), goalId, items.size(), schedule.getStartDate(), schedule.getEndDate());
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
        List<ScheduleItem> items = scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAscForUpdate(scheduleId);
        List<Long> taskIds = items.stream().map(ScheduleItem::getLearningTaskId)
                .filter(java.util.Objects::nonNull).distinct().toList();
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
     * 동시 완료 처리(completeScheduleItem)와의 상태 유실(Lost Update)을 방지하기 위해 비관적 락으로 조회한다.
     */
    @Transactional
    public ScheduleResponseDto updateScheduleItem(
            Long memberId, Long scheduleId, Long itemId, ScheduleItemUpdateRequestDto request
    ) {
        Schedule schedule = getOwnedSchedule(memberId, scheduleId);
        MemberGoal goal = memberGoalRepository.findById(schedule.getGoalId())
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));

        List<ScheduleItem> items = scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAscForUpdate(scheduleId);
        ScheduleItem target = items.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ScheduleErrorCode.SCHEDULE_ITEM_NOT_FOUND));

        if (target.getStatus() == ScheduleItemStatus.FINISHED) {
            throw new CustomException(ScheduleErrorCode.SCHEDULE_ITEM_ALREADY_FINISHED);
        }

        LocalDate newDate = request.date();
        int newMinutes = request.allocatedMinutes() != null ? request.allocatedMinutes() : target.getAllocatedMinutes();
        if (newMinutes <= 0) {
            throw new CustomException(ScheduleErrorCode.INVALID_SCHEDULE_ITEM_MINUTES);
        }
        if (newDate.isBefore(LocalDate.now())) {
            throw new CustomException(ScheduleErrorCode.SCHEDULE_ITEM_DATE_IN_PAST);
        }
        Set<DayOfWeek> availableDays = resolveAvailableDays(memberId, goal);
        if (!availableDays.contains(newDate.getDayOfWeek())) {
            throw new CustomException(ScheduleErrorCode.SCHEDULE_ITEM_DATE_NOT_AVAILABLE);
        }

        LocalDate oldDate = target.getDate();
        List<ScheduleItem> othersOnNewDate = items.stream()
                .filter(item -> !item.getId().equals(itemId) && item.getDate().equals(newDate))
                .toList();
        int existingMinutesOnNewDate = othersOnNewDate.stream().mapToInt(ScheduleItem::getAllocatedMinutes).sum();
        int dailyCapacityMinutes = calculateDailyCapacityMinutes(memberId, goal, resolveAvailableDays(memberId, goal));
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

    /**
     * 스케줄 항목의 학습을 시작 상태(IN_PROGRESS)로 전환한다. 이미 완료(FINISHED)된 항목은
     * 시작 상태로 되돌아가지 않는다(멱등하게 그대로 FINISHED 유지).
     * 동시성 경합 시 상태 역전을 방지하기 위해 비관적 락으로 조회한다.
     */
    @Transactional
    public ScheduleItemResponseDto startScheduleItem(Long memberId, Long scheduleId, Long itemId) {
        getOwnedSchedule(memberId, scheduleId);
        ScheduleItem item = getOwnedScheduleItemForUpdate(scheduleId, itemId);
        item.start();
        return ScheduleItemResponseDto.from(item);
    }

    /**
     * 스케줄 항목의 학습 수행을 완료 처리한다. 통계(완료율/목표 진행률)는 이 status를 조회만 해서 계산하므로,
     * 이 메서드가 유일한 쓰기 경로다. 완료는 기본적으로 단방향이며, 이미 FINISHED인 항목에 다시 요청해도
     * 동일한 결과로 멱등하게 처리한다(중복 요청/네트워크 재시도에도 에러 없이 안전).
     * 동시성 경합 시 상태 역전을 방지하기 위해 비관적 락으로 조회한다.
     */
    @Transactional
    public ScheduleItemResponseDto completeScheduleItem(Long memberId, Long scheduleId, Long itemId) {
        getOwnedSchedule(memberId, scheduleId);
        ScheduleItem item = getOwnedScheduleItemForUpdate(scheduleId, itemId);
        item.finish();
        return ScheduleItemResponseDto.from(item);
    }

    /**
     * 완료를 잘못 누른 경우를 위한 취소(원복) 경로. 정상 흐름에서는 쓰이지 않는 예외 처리용이라
     * completeScheduleItem과 별도 메서드/엔드포인트로 둔다.
     * 동시성 경합 시 상태 역전을 방지하기 위해 비관적 락으로 조회한다.
     */
    @Transactional
    public ScheduleItemResponseDto revertScheduleItemCompletion(Long memberId, Long scheduleId, Long itemId) {
        getOwnedSchedule(memberId, scheduleId);
        ScheduleItem item = getOwnedScheduleItemForUpdate(scheduleId, itemId);
        item.revertCompletion();
        return ScheduleItemResponseDto.from(item);
    }

    @Transactional
    public ScheduleFailureResultDto failScheduleItem(Long memberId, Long scheduleId, Long itemId,
                                                      ScheduleItemFailureRequestDto request) {
        getOwnedSchedule(memberId, scheduleId);
        ScheduleItem item = getOwnedScheduleItemForUpdate(scheduleId, itemId);
        if (item.getLearningTaskId() == null || item.getStatus() == ScheduleItemStatus.FINISHED) {
            throw new IllegalArgumentException("Completed items and empty supplement slots cannot fail.");
        }
        item.fail();
        scheduleFailureRepository.save(ScheduleFailure.create(itemId, item.getLearningTaskId(), request.reasonCode(),
                request.reasonDetail(), request.action()));
        int failures = Math.toIntExact(scheduleFailureRepository.countByLearningTaskId(item.getLearningTaskId()));
        boolean splitRecommended = failures >= 3;
        if (request.action() == FailureHandlingAction.REPLAN_REMAINING) {
            replanRemainingInternal(memberId, scheduleId);
            return new ScheduleFailureResultDto(failures, false, false, splitRecommended, true,
                    splitRecommended ? "동일 태스크가 3회 이상 실패했습니다. 재배치 후 태스크 분할을 권유합니다."
                            : "완료하지 않은 태스크만 유지하여 남은 일정을 재조정했습니다.");
        }
        ScheduleItem supplement = scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAscForUpdate(scheduleId)
                .stream().filter(candidate -> candidate.getKind() == ScheduleItemKind.SUPPLEMENT
                        && candidate.getLearningTaskId() == null && candidate.getStatus() == ScheduleItemStatus.PENDING
                        && !candidate.getDate().isBefore(LocalDate.now())
                        && candidate.getAllocatedMinutes() >= item.getAllocatedMinutes()).findFirst().orElse(null);
        if (supplement == null) {
            return new ScheduleFailureResultDto(failures, false, true, splitRecommended, true,
                    splitRecommended ? "보충 시간이 부족하고 3회 이상 실패했습니다. 태스크 분할 또는 남은 일정 재조정을 권유합니다."
                            : "보충 시간이 부족합니다. 남은 일정 전체 재조정 또는 목표일 연장을 권유합니다.");
        }
        supplement.assignToSupplement(item.getLearningTaskId(), item.getTitle());
        return new ScheduleFailureResultDto(failures, true, false, splitRecommended, false,
                splitRecommended ? "보충 시간에 배치했습니다. 동일 태스크가 3회 이상 실패하여 분할도 권유합니다."
                        : "보충 시간에 실패한 태스크를 배치했습니다.");
    }

    /** User-initiated postponement: normal calendar dates are never accepted as a destination. */
    @Transactional
    public ScheduleItemResponseDto deferToSupplement(Long memberId, Long scheduleId, Long itemId) {
        getOwnedSchedule(memberId, scheduleId);
        ScheduleItem source = getOwnedScheduleItemForUpdate(scheduleId, itemId);
        if (source.getLearningTaskId() == null || source.getStatus() == ScheduleItemStatus.FINISHED) {
            throw new IllegalArgumentException("Only an unfinished task can be deferred.");
        }
        ScheduleItem destination = scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAscForUpdate(scheduleId)
                .stream().filter(item -> item.getKind() == ScheduleItemKind.SUPPLEMENT
                        && item.getLearningTaskId() == null && !item.getDate().isBefore(LocalDate.now())
                        && item.getAllocatedMinutes() >= source.getAllocatedMinutes()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No supplement day has enough remaining time."));
        destination.assignToSupplement(source.getLearningTaskId(), source.getTitle());
        source.clearTask();
        return ScheduleItemResponseDto.from(destination);
    }

    @Transactional
    public ScheduleResponseDto replanRemaining(Long memberId, Long scheduleId) {
        return replanRemainingInternal(memberId, scheduleId);
    }

    /** Keeps FINISHED items and recreates only failed/pending/in-progress task placements. */
    private ScheduleResponseDto replanRemainingInternal(Long memberId, Long scheduleId) {
        Schedule schedule = getOwnedSchedule(memberId, scheduleId);
        MemberGoal goal = getOwnedGoalForUpdate(memberId, schedule.getGoalId());
        List<ScheduleItem> all = scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAscForUpdate(scheduleId);
        List<Long> taskIds = all.stream().filter(item -> item.getStatus() != ScheduleItemStatus.FINISHED
                        && item.getLearningTaskId() != null).map(ScheduleItem::getLearningTaskId).distinct().toList();
        if (taskIds.isEmpty()) return ScheduleResponseDto.of(schedule, all);
        java.util.Map<Long, LearningTask> byId = learningTaskRepository.findAllById(taskIds).stream()
                .collect(Collectors.toMap(LearningTask::getId, task -> task));
        List<LearningTask> remaining = taskIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        validateAllocatedMinutes(remaining);
        List<ScheduleItem> replaceable = all.stream().filter(item -> item.getStatus() != ScheduleItemStatus.FINISHED).toList();
        scheduleItemRepository.deleteAll(replaceable);
        scheduleItemRepository.flush();
        Set<DayOfWeek> availableDays = resolveAvailableDays(memberId, goal);
        int capacity = calculateDailyCapacityMinutes(memberId, goal, availableDays);
        ScheduleAllocator.AllocationPlan plan = new ScheduleAllocator().allocateWithSupplementDays(remaining, earliestStart(goal),
                availableDays, capacity, supplementEveryRegularDays(capacity), MAX_HORIZON_DAYS);
        List<ScheduleAllocator.AllocatedItem> allocations = plan.regularItems();
        List<ScheduleItem> replanned = createItemsWithSupplementDays(schedule, plan, capacity);
        scheduleItemRepository.saveAll(replanned);
        LocalDate start = all.stream().filter(item -> item.getStatus() == ScheduleItemStatus.FINISHED)
                .map(ScheduleItem::getDate).min(LocalDate::compareTo).orElse(allocations.getFirst().date());
        LocalDate end = replanned.stream().map(ScheduleItem::getDate).max(LocalDate::compareTo).orElse(start);
        schedule.updatePeriod(start, end);
        List<ScheduleItem> response = new ArrayList<>(all.stream()
                .filter(item -> item.getStatus() == ScheduleItemStatus.FINISHED).toList());
        response.addAll(replanned);
        response.sort(Comparator.comparing(ScheduleItem::getDate).thenComparingInt(ScheduleItem::getOrderIndex));
        return ScheduleResponseDto.of(schedule, response);
    }

    private List<ScheduleItem> createItemsWithSupplementDays(Schedule schedule,
            ScheduleAllocator.AllocationPlan plan, int dailyCapacityMinutes) {
        List<ScheduleItem> items = createRegularItems(schedule, plan.regularItems());
        for (LocalDate date : plan.supplementDates()) {
            items.add(ScheduleItem.createSupplement(schedule, date, date.getDayOfWeek(), dailyCapacityMinutes, 1));
        }
        items.sort(Comparator.comparing(ScheduleItem::getDate).thenComparingInt(ScheduleItem::getOrderIndex));
        return items;
    }

    private int supplementEveryRegularDays(int dailyCapacityMinutes) {
        if (dailyCapacityMinutes <= 60) return 4;
        if (dailyCapacityMinutes <= 120) return 3;
        return 2;
    }

    private void insertBreaksAndAssignTimeRanges(Long memberId, List<ScheduleItem> items, int breakMinutes) {
        java.util.Map<LocalDate, List<ScheduleItem>> byDate = items.stream().collect(
                Collectors.groupingBy(ScheduleItem::getDate, java.util.TreeMap::new, Collectors.toList()));
        List<ScheduleItem> result = new ArrayList<>();
        for (List<ScheduleItem> dayItems : byDate.values()) {
            List<ScheduleItem> regulars = dayItems.stream().filter(item -> item.getKind() == ScheduleItemKind.REGULAR)
                    .sorted(Comparator.comparingInt(ScheduleItem::getOrderIndex)).toList();
            List<ScheduleItem> supplements = dayItems.stream().filter(item -> item.getKind() == ScheduleItemKind.SUPPLEMENT).toList();
            int order = 1;
            for (int i = 0; i < regulars.size(); i++) {
                ScheduleItem item = regulars.get(i); item.updateOrderIndex(order++); result.add(item);
                if (breakMinutes > 0 && i < regulars.size() - 1) {
                    result.add(ScheduleItem.createBreak(item.getSchedule(), item.getDate(), item.getDayOfWeek(), breakMinutes, order++));
                }
            }
            for (ScheduleItem supplement : supplements) { supplement.updateOrderIndex(order++); result.add(supplement); }
        }
        items.clear(); items.addAll(result);
        if (availabilityRepository == null) return;
        java.util.Map<java.time.DayOfWeek, List<Availability>> availabilityByDay = availabilityRepository
                .findAllByMemberIdOrderByDayOfWeekAscStartTimeAsc(memberId).stream().filter(Availability::isEnabled)
                .collect(Collectors.groupingBy(Availability::getDayOfWeek));
        for (List<ScheduleItem> dayItems : byDate.values()) {
            LocalDate date = dayItems.getFirst().getDate();
            List<Availability> windows = availabilityByDay.getOrDefault(date.getDayOfWeek(), List.of());
            if (windows.isEmpty()) continue;
            int windowIndex = 0; LocalTime cursor = windows.getFirst().getStartTime();
            for (ScheduleItem item : items.stream().filter(candidate -> candidate.getDate().equals(date))
                    .sorted(Comparator.comparingInt(ScheduleItem::getOrderIndex)).toList()) {
                int minutes = item.getAllocatedMinutes();
                while (windowIndex < windows.size() && !fitsInWindow(cursor, minutes, windows.get(windowIndex).getEndTime())) {
                    windowIndex++; if (windowIndex < windows.size()) cursor = windows.get(windowIndex).getStartTime();
                }
                if (windowIndex >= windows.size()) throw new CustomException(ScheduleErrorCode.SCHEDULE_GENERATION_FAILED);
                item.assignTimeRange(cursor, cursor.plusMinutes(minutes));
                cursor = cursor.plusMinutes(minutes);
            }
        }
    }

    private List<ScheduleItem> createRegularItems(Schedule schedule, List<ScheduleAllocator.AllocatedItem> allocations) {
        List<ScheduleItem> items = new ArrayList<>();
        for (ScheduleAllocator.AllocatedItem allocation : allocations) {
            items.add(ScheduleItem.create(schedule, allocation.task().getId(), allocation.task().getTitle(),
                    allocation.date(), allocation.date().getDayOfWeek(), allocation.allocatedMinutes(), allocation.orderInDay()));
        }
        return items;
    }

    private boolean fitsInWindow(LocalTime start, int minutes, LocalTime end) {
        int startMinute = start.getHour() * 60 + start.getMinute();
        int endExclusiveMinute = end.equals(LocalTime.of(23, 59)) ? 1440 : end.getHour() * 60 + end.getMinute();
        return startMinute + minutes <= endExclusiveMinute;
    }

    private ScheduleItem getOwnedScheduleItemForUpdate(Long scheduleId, Long itemId) {
        return scheduleItemRepository.findByIdAndScheduleIdForUpdate(itemId, scheduleId)
                .orElseThrow(() -> new CustomException(ScheduleErrorCode.SCHEDULE_ITEM_NOT_FOUND));
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

    private MemberGoal getOwnedGoalForUpdate(Long memberId, Long goalId) {
        MemberGoal goal = memberGoalRepository.findByIdForUpdate(goalId)
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

    /**
     * Time windows are the source of truth for the new planner. The allocator has
     * one daily capacity, therefore it uses the smallest enabled weekday capacity
     * to guarantee that no selected weekday is overbooked. Legacy goals without
     * windows retain their dailyStudyHours behavior.
     */
    private int calculateDailyCapacityMinutes(Long memberId, MemberGoal goal, Set<DayOfWeek> availableDays) {
        if (availabilityRepository != null) {
            java.util.Map<DayOfWeek, Integer> minutesByDay = availabilityRepository
                    .findAllByMemberIdOrderByDayOfWeekAscStartTimeAsc(memberId).stream()
                    .filter(Availability::isEnabled)
                    .collect(Collectors.groupingBy(Availability::getDayOfWeek,
                            Collectors.summingInt(Availability::getAvailableMinutes)));
            int windowCapacity = availableDays.stream().map(minutesByDay::get)
                    .filter(java.util.Objects::nonNull).min(Integer::compareTo).orElse(0);
            if (windowCapacity > 0) return windowCapacity;
        }
        if (goal.getDailyStudyHours() == null || goal.getDailyStudyHours() <= 0) {
            throw new CustomException(ScheduleErrorCode.INVALID_DAILY_STUDY_HOURS);
        }
        return goal.getDailyStudyHours() * 60;
    }

    private Set<DayOfWeek> resolveAvailableDays(Long memberId, MemberGoal goal) {
        if (availabilityRepository != null) {
            Set<DayOfWeek> daysWithWindows = availabilityRepository
                    .findAllByMemberIdOrderByDayOfWeekAscStartTimeAsc(memberId).stream()
                    .filter(Availability::isEnabled).map(Availability::getDayOfWeek)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!daysWithWindows.isEmpty()) return daysWithWindows;
        }
        return parseAvailableDays(goal.getAvailableDays());
    }
}
