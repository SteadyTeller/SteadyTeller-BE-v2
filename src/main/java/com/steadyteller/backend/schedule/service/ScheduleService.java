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
import com.steadyteller.backend.schedule.entity.ScheduleFailure;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * MemberGoal + 확정된 LearningTask 목록을 바탕으로 일자별 학습 스케줄을 생성/조회한다 (설계 명세 4번).
 * <p>
 * 스케줄 생성(generateSchedule)은 {@link #getOwnedGoalForUpdate}로 MemberGoal 로우에 비관적 락을 먼저 잡고 실행되어,
 * 목표 삭제(deleteGoal) 및 태스크 확정(confirmTasks)과의 동시성 경합 시 고아 데이터 발생 및 참조 무결성 파괴를 원천 방지한다.
 */
@Service
@Slf4j
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
    private final ScheduleFailureRepository scheduleFailureRepository;
    private final AvailabilityRepository availabilityRepository;

    @Transactional
    public ScheduleResponseDto generateSchedule(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoalForUpdate(memberId, goalId);
        log.info("Schedule generation started: memberId={}, goalId={}, title={}", memberId, goalId, goal.getTitle());
        List<LearningTask> confirmedTasks =
                learningTaskRepository.findUnscheduledByGoalIdAndStatusForUpdate(goalId, LearningTaskStatus.PENDING);
        if (confirmedTasks.isEmpty()) {
            log.warn("Schedule generation rejected: no confirmed pending tasks. goalId={}", goalId);
            throw new CustomException(ScheduleErrorCode.NO_CONFIRMED_TASKS);
        }
        validateAllocatedMinutes(confirmedTasks);

        LocalDate earliestStart = earliestStart(goal);
        List<Availability> availabilities = availabilityRepository
                .findAllByMemberGoalIdOrderByDayOfWeekAscStartTimeAsc(goalId);
        List<ScheduleAllocator.TimeSlotAllocation> plannedAllocations = new ScheduleAllocator().allocateByWindows(
                confirmedTasks, earliestStart, goal.getTargetDate(), availabilities);
        log.info("Schedule allocation completed: goalId={}, taskCount={}, allocationCount={}",
                goalId, confirmedTasks.size(), plannedAllocations.size());
        Schedule schedule = scheduleRepository.save(Schedule.create(
                memberId,
                goalId,
                plannedAllocations.getFirst().date(),
                plannedAllocations.getLast().date()
        ));

        List<ScheduleItem> items = createRegularItems(schedule, plannedAllocations);
        scheduleItemRepository.saveAll(items);
        schedule.updatePeriod(schedule.getStartDate(), items.stream().map(ScheduleItem::getDate)
                .max(LocalDate::compareTo).orElse(schedule.getEndDate()));

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
        scheduleItemRepository.deleteAll(items);
        scheduleItemRepository.flush();
        synchronizeTaskStatuses(taskIds);
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
        if (newMinutes <= 0 || newMinutes > MAX_ALLOCATED_MINUTES) {
            throw new CustomException(ScheduleErrorCode.INVALID_SCHEDULE_ITEM_MINUTES);
        }
        if (newDate.isBefore(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")))) {
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
        synchronizeTaskStatus(item.getLearningTaskId());
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
        synchronizeTaskStatus(item.getLearningTaskId());
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
        synchronizeTaskStatus(item.getLearningTaskId());
        return ScheduleItemResponseDto.from(item);
    }

    @Transactional
    public ScheduleFailureResultDto failScheduleItem(Long memberId, Long scheduleId, Long itemId,
                                                      ScheduleItemFailureRequestDto request) {
        getOwnedSchedule(memberId, scheduleId);
        ScheduleItem item = getOwnedScheduleItemForUpdate(scheduleId, itemId);
        if (item.getLearningTaskId() == null || item.getStatus() == ScheduleItemStatus.FINISHED) {
            throw new IllegalArgumentException("Completed schedule items cannot fail.");
        }
        if (item.getStatus() == ScheduleItemStatus.FAILED) {
            int failures = Math.toIntExact(scheduleFailureRepository.countByLearningTaskId(item.getLearningTaskId()));
            return new ScheduleFailureResultDto(failures, "이미 실패 처리된 일정입니다.");
        }
        item.fail();
        scheduleFailureRepository.save(ScheduleFailure.create(itemId, item.getLearningTaskId(), request.reasonCode(),
                request.reasonDetail()));
        int failures = Math.toIntExact(scheduleFailureRepository.countByLearningTaskId(item.getLearningTaskId()));
        synchronizeTaskStatus(item.getLearningTaskId());
        return new ScheduleFailureResultDto(failures, "일정 항목을 실패 처리했습니다.");
    }

    private void assignTimeRanges(Long goalId, List<ScheduleItem> items) {
        java.util.Map<LocalDate, List<ScheduleItem>> byDate = items.stream().collect(
                Collectors.groupingBy(ScheduleItem::getDate, java.util.TreeMap::new, Collectors.toList()));
        for (List<ScheduleItem> dayItems : byDate.values()) {
            List<ScheduleItem> orderedItems = dayItems.stream()
                    .sorted(Comparator.comparingInt(ScheduleItem::getOrderIndex)).toList();
            int order = 1;
            for (ScheduleItem item : orderedItems) item.updateOrderIndex(order++);
        }
        java.util.Map<java.time.DayOfWeek, List<Availability>> availabilityByDay = availabilityRepository
                .findAllByMemberGoalIdOrderByDayOfWeekAscStartTimeAsc(goalId).stream().filter(Availability::isEnabled)
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

    private List<ScheduleItem> createRegularItems(Schedule schedule,
                                                   List<ScheduleAllocator.TimeSlotAllocation> allocations) {
        List<ScheduleItem> items = new ArrayList<>();
        for (ScheduleAllocator.TimeSlotAllocation allocation : allocations) {
            items.add(ScheduleItem.create(schedule, allocation.task().getId(), allocation.task().getTitle(),
                    allocation.date(), allocation.date().getDayOfWeek(), allocation.allocatedMinutes(), allocation.orderInDay()));
            items.getLast().assignTimeRange(allocation.startTime(), allocation.endTime());
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
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        return goal.getStartDate().isAfter(today) ? goal.getStartDate() : today;
    }

    /** Time windows are the sole availability source for the planner. */
    private int calculateDailyCapacityMinutes(Long memberId, MemberGoal goal, Set<DayOfWeek> availableDays) {
        java.util.Map<DayOfWeek, Integer> minutesByDay = availabilityRepository
                .findAllByMemberGoalIdOrderByDayOfWeekAscStartTimeAsc(goal.getId()).stream()
                .filter(Availability::isEnabled)
                .collect(Collectors.groupingBy(Availability::getDayOfWeek,
                        Collectors.summingInt(Availability::getAvailableMinutes)));
        int windowCapacity = availableDays.stream().map(minutesByDay::get)
                .filter(java.util.Objects::nonNull).min(Integer::compareTo).orElse(0);
        if (windowCapacity > 0) return windowCapacity;
        throw new CustomException(ScheduleErrorCode.INVALID_AVAILABLE_DAYS);
    }

    private Set<DayOfWeek> resolveAvailableDays(Long memberId, MemberGoal goal) {
        Set<DayOfWeek> daysWithWindows = availabilityRepository
                .findAllByMemberGoalIdOrderByDayOfWeekAscStartTimeAsc(goal.getId()).stream()
                .filter(Availability::isEnabled).map(Availability::getDayOfWeek)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!daysWithWindows.isEmpty()) return daysWithWindows;
        throw new CustomException(ScheduleErrorCode.INVALID_AVAILABLE_DAYS);
    }

    private void recalculateTimeRangesForDate(Long goalId, java.time.LocalDate date, List<ScheduleItem> allItems) {
        if (availabilityRepository == null) return;
        List<com.steadyteller.backend.member.domain.Availability> windows = availabilityRepository
                .findAllByMemberGoalIdOrderByDayOfWeekAscStartTimeAsc(goalId).stream()
                .filter(com.steadyteller.backend.member.domain.Availability::isEnabled)
                .filter(a -> a.getDayOfWeek() == date.getDayOfWeek())
                .toList();
        if (windows.isEmpty()) return;

        int windowIndex = 0;
        java.time.LocalTime cursor = windows.get(0).getStartTime();

        List<ScheduleItem> itemsOnDate = allItems.stream()
                .filter(item -> item.getDate().equals(date))
                .sorted(java.util.Comparator.comparingInt(ScheduleItem::getOrderIndex))
                .toList();
                
        for (ScheduleItem item : itemsOnDate) {
            int minutes = item.getAllocatedMinutes();
            while (windowIndex < windows.size()) {
                if (fitsInWindow(cursor, minutes, windows.get(windowIndex).getEndTime())) break;
                java.time.LocalTime wStart = windows.get(windowIndex).getStartTime();
                java.time.LocalTime wEnd = windows.get(windowIndex).getEndTime();
                int startMin = wStart.getHour() * 60 + wStart.getMinute();
                int endMin = wEnd.equals(java.time.LocalTime.of(23, 59)) ? 1440 : wEnd.getHour() * 60 + wEnd.getMinute();
                if (minutes > (endMin - startMin)) break;
                windowIndex++;
                if (windowIndex < windows.size()) cursor = windows.get(windowIndex).getStartTime();
            }
            if (windowIndex >= windows.size()) windowIndex = windows.size() - 1;
            
            int startM = cursor.getHour() * 60 + cursor.getMinute();
            int endM = Math.min(1439, startM + minutes);
            int eh = endM / 60; if (eh == 24) eh = 23;
            int em = endM % 60; if (endM == 1439) em = 59;
            java.time.LocalTime calculatedEnd = java.time.LocalTime.of(eh, em);
            item.assignTimeRange(cursor, calculatedEnd);
            cursor = calculatedEnd;
        }
    }

    private void synchronizeTaskStatuses(List<Long> taskIds) {
        taskIds.stream().filter(java.util.Objects::nonNull).distinct().forEach(this::synchronizeTaskStatus);
    }

    /** Derives a task's progress exclusively from the schedule items currently assigned to it. */
    private void synchronizeTaskStatus(Long taskId) {
        if (taskId == null) {
            return;
        }
        learningTaskRepository.findById(taskId).ifPresent(task -> {
            List<ScheduleItem> items = scheduleItemRepository.findByLearningTaskIdOrderByDateAscOrderIndexAsc(taskId);
            LearningTaskStatus status;
            if (!items.isEmpty() && items.stream().allMatch(item -> item.getStatus() == ScheduleItemStatus.FINISHED)) {
                status = LearningTaskStatus.FINISHED;
            } else if (items.stream().anyMatch(item -> item.getStatus() == ScheduleItemStatus.IN_PROGRESS)) {
                status = LearningTaskStatus.IN_PROGRESS;
            } else if (items.stream().anyMatch(item -> item.getStatus() == ScheduleItemStatus.FAILED)) {
                status = LearningTaskStatus.FAILED;
            } else {
                status = LearningTaskStatus.PENDING;
            }
            task.updateStatus(status);
        });
    }
}
