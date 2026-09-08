package com.steadyteller.backend.schedule.service;

import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.schedule.dto.AiSchedulePlanResponseDto;
import com.steadyteller.backend.schedule.dto.AiSchedulePlanResponseDto.DailyPlanDto;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * AI를 활용해 일자별 학습 스케줄을 생성한다 (설계 명세 4번).
 *
 * 3계층 하이브리드 배정 전략:
 * - Tier 1: AI가 과목 병행(Interleaving)과 분산 학습을 고려해 직접 배정한 일자별 계획(dailyPlans)을
 *          엄격한 수치/제약조건 검증(ID 일치, 요일 준수, 일일 시간 한도 등) 후 무결할 때 최우선 채택한다.
 * - Tier 2: 일자별 직배정에 수치 계산 환각이 있을 경우, AI가 함께 반환한 순서(fallbackTaskOrder)를 바탕으로
 *          서버의 결정론적 알고리즘(ScheduleAllocator)을 통해 안전하게 배정한다.
 * - Tier 3: AI 호출 실패 또는 순서마저 유효하지 않을 경우, 기본 순서(난이도 오름차순)로 ScheduleAllocator를 실행한다.
 *
 * =========================================================================================
 * [FUTURE EXTENSION: 방법 ③ 태스크 분할(Task Splitting / Chunking) 전환 가이드]
 * =========================================================================================
 * 향후 긴 태스크(예: 70분)를 일자별로 쪼개어(예: Day1에 60분, Day2에 10분) 배정하는
 * 완전 분할 모델로 전환할 경우, 아래 DTO 및 프롬프트 템플릿을 바로 활용할 수 있습니다.
 *
 * 1. AI 응답 DTO 확장 예시:
 *    public record AiSplitScheduleResponseDto(
 *        List<DailySplitPlanDto> dailyPlans
 *    ) {
 *        public record DailySplitPlanDto(
 *            LocalDate date,
 *            List<TaskChunkDto> taskChunks
 *        ) {}
 *
 *        public record TaskChunkDto(
 *            Long taskId,
 *            int minutesForToday,   // 당일 배정할 분량 (예: 60)
 *            int remainingMinutes,  // 이 날짜 이후 남은 미학습 분량 (예: 10)
 *            boolean isCompleted    // 당일에 완전히 끝나는지 여부
 *        ) {}
 *    }
 *
 * 2. 태스크 분할 전환용 AI 프롬프트 템플릿:
 *    \"\"\"
 *    당신은 전문 학습 코칭 AI입니다. 아래 확정된 태스크들을 [사용 가능한 날짜 슬롯]에 배정하세요.
 *    - 하루 최대 학습 시간: %d분
 *    - 만약 어떤 태스크(예: 70분)가 당일 남은 시간(예: 60분)보다 길다면, 태스크를 두 날짜로 분할하여 배정하세요.
 *      (예: Day 1에 60분 배정, Day 2에 나머지 10분 배정)
 *    - 각 태스크의 모든 분할 시간(minutesForToday)의 총합은 원래 태스크의 allocatedMinutes와 정확히 일치해야 합니다.
 *    - 서로 다른 과목(category/subject)을 하루에 적절히 병행하여 지루함을 줄이세요.
 *    \"\"\"
 * =========================================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleAiService {

    private final ChatClient chatClient;
    private final ScheduleAllocator scheduleAllocator;

    public List<ScheduleAllocator.AllocatedItem> generateSchedule(
            MemberGoal goal,
            List<LearningTask> confirmedTasks,
            LocalDate earliestStart,
            Set<DayOfWeek> availableDays,
            int dailyCapacityMinutes,
            int maxHorizonDays
    ) {
        List<Long> validIds = confirmedTasks.stream().map(LearningTask::getId).toList();
        Map<Long, LearningTask> taskMap = confirmedTasks.stream()
                .collect(Collectors.toMap(LearningTask::getId, Function.identity()));

        try {
            List<LocalDate> dateSlots = calculateAvailableSlots(earliestStart, availableDays, confirmedTasks, dailyCapacityMinutes);
            String prompt = buildPrompt(goal, confirmedTasks, dateSlots, dailyCapacityMinutes);

            AiSchedulePlanResponseDto response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(AiSchedulePlanResponseDto.class);

            if (response != null) {
                // Tier 1: AI 일자별 직배정 검증 및 채택
                Optional<List<ScheduleAllocator.AllocatedItem>> directPlan = validateAndBuildDirectPlan(
                        response.dailyPlans(), taskMap, validIds, earliestStart, availableDays, dailyCapacityMinutes
                );
                if (directPlan.isPresent()) {
                    log.info("AI 일자별 직배정(Tier 1) 검증 성공으로 스케줄을 채택합니다. goalId={}", goal.getId());
                    return directPlan.get();
                }

                // Tier 2: AI 순서 기반 서버 결정론적 배정
                List<Long> fallbackOrder = response.fallbackTaskOrder();
                if (isValidPermutation(fallbackOrder, validIds)) {
                    log.warn("AI 일자별 직배정 검증 실패로 AI 순서 기반 배정(Tier 2)으로 폴백합니다. goalId={}", goal.getId());
                    List<LearningTask> orderedTasks = fallbackOrder.stream().map(taskMap::get).toList();
                    return scheduleAllocator.allocate(orderedTasks, earliestStart, availableDays, dailyCapacityMinutes, maxHorizonDays);
                }
            }
            log.warn("AI 응답이 유효하지 않아 기본 난이도순 배정(Tier 3)으로 폴백합니다. goalId={}", goal.getId());
        } catch (Exception e) {
            log.warn("AI 스케줄 생성 호출 실패로 기본 난이도순 배정(Tier 3)으로 폴백합니다. goalId={}, cause={}",
                    goal.getId(), e.getMessage());
        }

        // Tier 3: 기본 순서(난이도 오름차순) 기반 서버 배정
        List<LearningTask> defaultOrderedTasks = defaultOrder(confirmedTasks);
        return scheduleAllocator.allocate(defaultOrderedTasks, earliestStart, availableDays, dailyCapacityMinutes, maxHorizonDays);
    }

    /**
     * Tier 1 직배정 결과에 대한 엄격한 수치/제약조건 무결성 검증
     */
    private Optional<List<ScheduleAllocator.AllocatedItem>> validateAndBuildDirectPlan(
            List<DailyPlanDto> dailyPlans,
            Map<Long, LearningTask> taskMap,
            List<Long> validIds,
            LocalDate earliestStart,
            Set<DayOfWeek> availableDays,
            int dailyCapacityMinutes
    ) {
        if (dailyPlans == null || dailyPlans.isEmpty()) {
            return Optional.empty();
        }

        List<Long> assignedIds = new ArrayList<>();
        LocalDate previousDate = null;
        List<ScheduleAllocator.AllocatedItem> result = new ArrayList<>();

        for (DailyPlanDto plan : dailyPlans) {
            LocalDate date = plan.date();
            List<Long> taskIds = plan.taskIds();

            if (date == null || taskIds == null || taskIds.isEmpty()) {
                return Optional.empty();
            }

            // 1. 날짜 유효성 및 가용 요일 검증
            if (date.isBefore(earliestStart) || !availableDays.contains(date.getDayOfWeek())) {
                return Optional.empty();
            }

            // 2. 날짜 오름차순 검증 (날짜 역전 또는 동일 일자 중복 방지)
            if (previousDate != null && !date.isAfter(previousDate)) {
                return Optional.empty();
            }
            previousDate = date;

            // 3. 일일 학습 시간 검증
            int dailyTotalMinutes = 0;
            int orderInDay = 1;
            for (Long taskId : taskIds) {
                LearningTask task = taskMap.get(taskId);
                if (task == null) {
                    return Optional.empty();
                }
                assignedIds.add(taskId);
                dailyTotalMinutes += task.getAllocatedMinutes();
                result.add(new ScheduleAllocator.AllocatedItem(task, date, task.getAllocatedMinutes(), orderInDay++));
            }

            // 단일 태스크가 하루 한도를 초과하는 경우 단독 배정만 허용
            if (taskIds.size() == 1) {
                // 단일 태스크는 한도를 넘겨도 허용
            } else if (dailyTotalMinutes > dailyCapacityMinutes) {
                return Optional.empty(); // 여러 태스크 합이 일일 한도를 초과하면 환각으로 판단
            }
        }

        // 4. 모든 태스크가 누락/중복 없이 정확히 1번씩 배정되었는지 검증
        if (!isValidPermutation(assignedIds, validIds)) {
            return Optional.empty();
        }

        return Optional.of(result);
    }

    private boolean isValidPermutation(List<Long> candidateIds, List<Long> validIds) {
        return candidateIds != null
                && candidateIds.size() == validIds.size()
                && new HashSet<>(candidateIds).equals(new HashSet<>(validIds));
    }

    private List<LearningTask> defaultOrder(List<LearningTask> tasks) {
        return tasks.stream()
                .sorted(Comparator.comparing(LearningTask::getDifficulty).thenComparing(LearningTask::getId))
                .toList();
    }

    // 패키지 전용으로 열어 슬롯 상한(캡) 동작을 단위 테스트에서 직접 검증할 수 있게 한다.
    static final int MAX_DATE_SLOTS = 120;

    List<LocalDate> calculateAvailableSlots(
            LocalDate earliestStart,
            Set<DayOfWeek> availableDays,
            List<LearningTask> tasks,
            int dailyCapacityMinutes
    ) {
        int totalMinutes = tasks.stream().mapToInt(LearningTask::getAllocatedMinutes).sum();
        int estimatedDays = (int) Math.ceil((double) totalMinutes / Math.max(dailyCapacityMinutes, 1));
        // 하한(최소 10개)은 태스크가 적어도 AI가 스케줄을 짤 여유를 주기 위함이고,
        // 상한(MAX_DATE_SLOTS)은 dailyCapacityMinutes가 비정상적으로 작거나 태스크가 아주 많을 때
        // 프롬프트가 감당 못할 만큼 길어지는 것을 막기 위한 안전장치다.
        int slotCount = Math.min(Math.max(estimatedDays * 2, 10), MAX_DATE_SLOTS);

        List<LocalDate> slots = new ArrayList<>();
        LocalDate cursor = earliestStart;
        while (slots.size() < slotCount) {
            if (availableDays.contains(cursor.getDayOfWeek())) {
                slots.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return slots;
    }

    private String buildPrompt(
            MemberGoal goal,
            List<LearningTask> tasks,
            List<LocalDate> dateSlots,
            int dailyCapacityMinutes
    ) {
        String taskLines = tasks.stream()
                .map(task -> "- id=%d, title=%s, category=%s, subject=%s, difficulty=%d, allocatedMinutes=%d"
                        .formatted(task.getId(), task.getTitle(), task.getCategory(), task.getSubject(),
                                task.getDifficulty(), task.getAllocatedMinutes()))
                .collect(Collectors.joining("\n"));

        String slotLines = dateSlots.stream()
                .map(date -> "- %s (%s)".formatted(date.toString(), date.getDayOfWeek().name().substring(0, 3)))
                .collect(Collectors.joining("\n"));

        return """
                당신은 전문 학습 코칭 AI입니다. 아래 학습 목표와 확정된 태스크 목록, 그리고 학습 가능 날짜 목록을 참고하여
                사용자에게 최적의 일자별 학습 스케줄을 배정하세요.

                [학습 목표]
                - 목표명: %s
                - 현재 수준: %s
                - 집중 학습 분야: %s
                - 1일 최대 학습 시간: %d분

                [사용 가능한 날짜 슬롯]
                %s

                [확정된 태스크 목록]
                %s

                [배정 규칙]
                1. dailyPlans:
                   - 위 [사용 가능한 날짜 슬롯]에 있는 날짜만 사용하여 일자별로 taskIds를 배정하세요.
                   - 각 날짜에 배정된 태스크들의 소요 시간(allocatedMinutes) 합계는 하루 최대 %d분을 넘지 않아야 합니다. (단, 단일 태스크 자체가 %d분 이상인 경우 해당 날짜에 단독 배치하세요)
                   - 한 가지 주제만 며칠 동안 연속으로 몰아서 하기보다, 서로 다른 카테고리/주제(category/subject)를 하루에 적절히 병행(Interleaving)하거나 선수 개념을 먼저 학습하도록 균형 있게 분배하세요.
                2. fallbackTaskOrder:
                   - 만약 일자별 직배정에 문제가 있을 경우를 대비하여, 전체 태스크 ID의 최적 1차원 실행 순서를 함께 제공하세요.
                3. 공통 무결성 규칙:
                   - 모든 확정된 태스크 ID는 dailyPlans와 fallbackTaskOrder에 각각 정확히 1번씩만 포함되어야 합니다.
                   - 없는 ID를 지어내거나 기존 ID를 누락하지 마세요.
                """.formatted(goal.getTitle(), goal.getCurrentLevel(), goal.getFocusArea(),
                dailyCapacityMinutes, slotLines, taskLines, dailyCapacityMinutes, dailyCapacityMinutes);
    }
}
