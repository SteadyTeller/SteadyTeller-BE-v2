package com.steadyteller.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.schedule.dto.AiSchedulePlanResponseDto;
import com.steadyteller.backend.schedule.dto.AiSchedulePlanResponseDto.DailyPlanDto;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduleAiServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private final ScheduleAllocator scheduleAllocator = new ScheduleAllocator();
    private ScheduleAiService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleAiService(chatClient, scheduleAllocator);
    }

    @Test
    void adoptsTier1DirectPlanWhenAiPlanIsValid() {
        // Given: 2개의 태스크(각 30분, 총 60분)를 2026-08-24(월) 하루에 병행 배치(Tier 1)
        LocalDate monday = LocalDate.of(2026, 8, 24);
        LearningTask task1 = task(1L, "데이터베이스 정규화", "DB", 2, 30);
        LearningTask task2 = task(2L, "네트워크 OSI 7계층", "네트워크", 3, 30);

        AiSchedulePlanResponseDto response = new AiSchedulePlanResponseDto(
                List.of(new DailyPlanDto(monday, List.of(2L, 1L))),
                List.of(1L, 2L)
        );
        given(chatClient.prompt().user(anyString()).call().entity(AiSchedulePlanResponseDto.class))
                .willReturn(response);

        // When
        List<ScheduleAllocator.AllocatedItem> result = service.generateSchedule(
                goal(), List.of(task1, task2), monday, Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), 60, 3650
        );

        // Then: Tier 1 직배정 결과(2L이 order 1, 1L이 order 2로 월요일에 배치)가 채택됨
        assertThat(result).hasSize(2);
        assertThat(result.get(0).task().getId()).isEqualTo(2L);
        assertThat(result.get(0).date()).isEqualTo(monday);
        assertThat(result.get(0).orderInDay()).isEqualTo(1);
        assertThat(result.get(1).task().getId()).isEqualTo(1L);
        assertThat(result.get(1).date()).isEqualTo(monday);
        assertThat(result.get(1).orderInDay()).isEqualTo(2);
    }

    @Test
    void fallsBackToTier2WhenTier1ExceedsDailyCapacity() {
        // Given: 하루 60분 한도인데 AI가 하루에 45분+45분=90분을 배치하여 Tier 1 검증 실패
        LocalDate monday = LocalDate.of(2026, 8, 24);
        LocalDate wednesday = LocalDate.of(2026, 8, 26);
        LearningTask task1 = task(1L, "정규화 기초", "DB", 2, 45);
        LearningTask task2 = task(2L, "정규화 심화", "DB", 4, 45);

        AiSchedulePlanResponseDto response = new AiSchedulePlanResponseDto(
                List.of(new DailyPlanDto(monday, List.of(2L, 1L))), // 90분 > 60분 (환각 발생)
                List.of(2L, 1L) // fallbackTaskOrder
        );
        given(chatClient.prompt().user(anyString()).call().entity(AiSchedulePlanResponseDto.class))
                .willReturn(response);

        // When
        List<ScheduleAllocator.AllocatedItem> result = service.generateSchedule(
                goal(), List.of(task1, task2), monday, Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), 60, 3650
        );

        // Then: Tier 1 실패 후 Tier 2(AI 순서 기반 ScheduleAllocator)가 동작하여 월요일/수요일로 분할 배치됨
        assertThat(result).hasSize(2);
        assertThat(result.get(0).task().getId()).isEqualTo(2L);
        assertThat(result.get(0).date()).isEqualTo(monday);
        assertThat(result.get(1).task().getId()).isEqualTo(1L);
        assertThat(result.get(1).date()).isEqualTo(wednesday);
    }

    @Test
    void fallsBackToTier2WhenTier1ContainsUnavailableDay() {
        // Given: 화요일(TUESDAY)은 가용 요일(availableDays)이 아님에도 AI가 배정함
        LocalDate monday = LocalDate.of(2026, 8, 24);
        LocalDate tuesday = LocalDate.of(2026, 8, 25);
        LocalDate wednesday = LocalDate.of(2026, 8, 26);
        LearningTask task1 = task(1L, "정규화 기초", "DB", 2, 30);
        LearningTask task2 = task(2L, "정규화 심화", "DB", 4, 30);

        AiSchedulePlanResponseDto response = new AiSchedulePlanResponseDto(
                List.of(
                        new DailyPlanDto(monday, List.of(1L)),
                        new DailyPlanDto(tuesday, List.of(2L)) // 화요일은 비가용일
                ),
                List.of(1L, 2L)
        );
        given(chatClient.prompt().user(anyString()).call().entity(AiSchedulePlanResponseDto.class))
                .willReturn(response);

        // When
        List<ScheduleAllocator.AllocatedItem> result = service.generateSchedule(
                goal(), List.of(task1, task2), monday, Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), 60, 3650
        );

        // Then: Tier 2 폴백되어 월요일에 두 태스크가 모두 배정됨(30+30=60분 <= 60분)
        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo(monday);
        assertThat(result.get(1).date()).isEqualTo(monday);
    }

    @Test
    void fallsBackToTier3WhenBothTier1AndTier2AreInvalid() {
        // Given: Tier 1은 ID 누락, Tier 2도 유효하지 않은 ID 포함
        LocalDate monday = LocalDate.of(2026, 8, 24);
        LearningTask hard = task(1L, "심화", "DB", 5, 30);
        LearningTask easy = task(2L, "기초", "DB", 1, 30);

        AiSchedulePlanResponseDto response = new AiSchedulePlanResponseDto(
                List.of(new DailyPlanDto(monday, List.of(1L))), // 2L 누락
                List.of(1L, 999L) // 999L 유령 ID
        );
        given(chatClient.prompt().user(anyString()).call().entity(AiSchedulePlanResponseDto.class))
                .willReturn(response);

        // When
        List<ScheduleAllocator.AllocatedItem> result = service.generateSchedule(
                goal(), List.of(hard, easy), monday, Set.of(DayOfWeek.MONDAY), 60, 3650
        );

        // Then: Tier 3(난이도 오름차순: easy(2L) -> hard(1L))으로 ScheduleAllocator 배정됨
        assertThat(result).hasSize(2);
        assertThat(result.get(0).task().getId()).isEqualTo(2L);
        assertThat(result.get(1).task().getId()).isEqualTo(1L);
    }

    @Test
    void adoptsTier1PlanWithOversizedSingleTaskExceedingDailyCapacity() {
        // Given: 하루 한도(60분)를 넘는 90분짜리 태스크 하나만 그날 단독 배치 — ScheduleAllocator와 동일한 예외 규칙이
        // Tier 1 검증에도 일관되게 적용되는지 확인한다.
        LocalDate monday = LocalDate.of(2026, 8, 24);
        LearningTask oversized = task(1L, "긴 태스크", "DB", 3, 90);

        AiSchedulePlanResponseDto response = new AiSchedulePlanResponseDto(
                List.of(new DailyPlanDto(monday, List.of(1L))),
                List.of(1L)
        );
        given(chatClient.prompt().user(anyString()).call().entity(AiSchedulePlanResponseDto.class))
                .willReturn(response);

        // When
        List<ScheduleAllocator.AllocatedItem> result = service.generateSchedule(
                goal(), List.of(oversized), monday, Set.of(DayOfWeek.MONDAY), 60, 3650
        );

        // Then: 단일 태스크 단독 배치는 한도 초과라도 Tier 1이 그대로 채택한다.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).task().getId()).isEqualTo(1L);
        assertThat(result.get(0).date()).isEqualTo(monday);
    }

    @Test
    void fallsBackWhenTier1DailyPlanContainsDuplicateTaskId() {
        // Given: 같은 날짜에 동일한 taskId(1L)가 두 번 배정됨(AI 환각) — 최종 순열 검증에서 걸러져야 한다.
        LocalDate monday = LocalDate.of(2026, 8, 24);
        LearningTask task1 = task(1L, "정규화 기초", "DB", 1, 30);
        LearningTask task2 = task(2L, "정규화 심화", "DB", 4, 30);

        AiSchedulePlanResponseDto response = new AiSchedulePlanResponseDto(
                List.of(new DailyPlanDto(monday, List.of(1L, 1L))), // 2L 누락 + 1L 중복
                List.of(2L, 1L) // fallbackTaskOrder는 유효한 순열
        );
        given(chatClient.prompt().user(anyString()).call().entity(AiSchedulePlanResponseDto.class))
                .willReturn(response);

        // When
        List<ScheduleAllocator.AllocatedItem> result = service.generateSchedule(
                goal(), List.of(task1, task2), monday, Set.of(DayOfWeek.MONDAY), 60, 3650
        );

        // Then: Tier 1은 거부되고 Tier 2(fallbackTaskOrder: 2L -> 1L)로 정상 배정된다.
        assertThat(result).hasSize(2);
        assertThat(result.get(0).task().getId()).isEqualTo(2L);
        assertThat(result.get(1).task().getId()).isEqualTo(1L);
    }

    @Test
    void calculateAvailableSlotsCapsSlotCountForVeryLargeTaskLoad() {
        // Given: 태스크가 아주 많고(50개 x 180분) dailyCapacityMinutes가 작아(30분) estimatedDays가 폭증하는 상황.
        // 상한(MAX_DATE_SLOTS) 없이는 프롬프트에 넣을 날짜 슬롯이 수백 개로 불어나 토큰/비용 문제가 생길 수 있다.
        List<LearningTask> manyTasks = java.util.stream.IntStream.rangeClosed(1, 50)
                .mapToObj(i -> task((long) i, "태스크" + i, "DB", 3, 180))
                .toList();

        List<LocalDate> slots = service.calculateAvailableSlots(
                LocalDate.of(2026, 8, 24), Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                manyTasks, 30
        );

        assertThat(slots).hasSize(ScheduleAiService.MAX_DATE_SLOTS);
    }

    @Test
    void calculateAvailableSlotsKeepsMinimumTenForSmallTaskLoad() {
        // Given: 태스크가 적으면(총 30분) 상한이 아니라 하한(최소 10개)이 적용되어야 한다.
        List<LearningTask> fewTasks = List.of(task(1L, "가벼운 태스크", "DB", 1, 30));

        List<LocalDate> slots = service.calculateAvailableSlots(
                LocalDate.of(2026, 8, 24), Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                fewTasks, 60
        );

        assertThat(slots).hasSize(10);
    }

    @Test
    void fallsBackToTier2WhenTier1DateExceedsTargetDate() {
        // Given: 목표 종료일(targetDate)이 2026-08-25인데 AI가 2026-08-26(수)에 배정함
        LocalDate monday = LocalDate.of(2026, 8, 24);
        LocalDate wednesday = LocalDate.of(2026, 8, 26);
        LearningTask task1 = task(1L, "정규화 기초", "DB", 2, 30);

        MemberGoal goalWithTightDeadline = MemberGoal.builder()
                .memberId(1L)
                .title("단기 완성")
                .startDate(monday)
                .targetDate(LocalDate.of(2026, 8, 25)) // 8월 25일까지
                .currentLevel("초급")
                .dailyStudyHours(1)
                .availableDays(List.of("MON", "WED"))
                .focusArea("DB")
                .build();
        ReflectionTestUtils.setField(goalWithTightDeadline, "id", 1L);

        AiSchedulePlanResponseDto response = new AiSchedulePlanResponseDto(
                List.of(new DailyPlanDto(wednesday, List.of(1L))), // targetDate 초과 배정
                List.of(1L)
        );
        given(chatClient.prompt().user(anyString()).call().entity(AiSchedulePlanResponseDto.class))
                .willReturn(response);

        // When
        List<ScheduleAllocator.AllocatedItem> result = service.generateSchedule(
                goalWithTightDeadline, List.of(task1), monday, Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), 60, 3650
        );

        // Then: Tier 1은 거부되고 Tier 2(월요일부터 순차 배정)로 정상 배정됨
        assertThat(result).hasSize(1);
        assertThat(result.get(0).task().getId()).isEqualTo(1L);
        assertThat(result.get(0).date()).isEqualTo(monday);
    }

    @Test
    void fallsBackToTier2WhenTier1DateNotInDateSlots() {
        // Given: AI가 2099년 같은 제공되지 않은 먼 날짜 슬롯을 반환함
        LocalDate monday = LocalDate.of(2026, 8, 24);
        LocalDate farFutureMonday = LocalDate.of(2099, 1, 4); // 월요일이지만 dateSlots에 없음
        LearningTask task1 = task(1L, "정규화 기초", "DB", 2, 30);

        AiSchedulePlanResponseDto response = new AiSchedulePlanResponseDto(
                List.of(new DailyPlanDto(farFutureMonday, List.of(1L))),
                List.of(1L)
        );
        given(chatClient.prompt().user(anyString()).call().entity(AiSchedulePlanResponseDto.class))
                .willReturn(response);

        // When
        List<ScheduleAllocator.AllocatedItem> result = service.generateSchedule(
                goal(), List.of(task1), monday, Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), 60, 3650
        );

        // Then: Tier 1은 거부되고 Tier 2에 의해 실제 earliestStart(월요일)에 배정됨
        assertThat(result).hasSize(1);
        assertThat(result.get(0).task().getId()).isEqualTo(1L);
        assertThat(result.get(0).date()).isEqualTo(monday);
    }

    @Test
    void fallsBackToTier3WhenAiCallThrows() {
        LocalDate monday = LocalDate.of(2026, 8, 24);
        LearningTask hard = task(1L, "심화", "DB", 5, 30);
        LearningTask easy = task(2L, "기초", "DB", 1, 30);

        given(chatClient.prompt().user(anyString()).call().entity(AiSchedulePlanResponseDto.class))
                .willThrow(new RuntimeException("AI API Timeout"));

        // When
        List<ScheduleAllocator.AllocatedItem> result = service.generateSchedule(
                goal(), List.of(hard, easy), monday, Set.of(DayOfWeek.MONDAY), 60, 3650
        );

        // Then: Tier 3(난이도순: easy -> hard)으로 배정됨
        assertThat(result).hasSize(2);
        assertThat(result.get(0).task().getId()).isEqualTo(2L);
        assertThat(result.get(1).task().getId()).isEqualTo(1L);
    }

    @Test
    void propagatesExceptionWhenAllocatorThrowsInTier2() {
        // Given: AI Tier 1은 무효하여 Tier 2로 넘어갔으나, allocator에서 maxHorizonDays 초과로 CustomException 발생 시
        // AI 예외 catch에 삼켜지지 않고 예외가 전파되는지 검증
        LocalDate monday = LocalDate.of(2026, 8, 24);
        LearningTask task1 = task(1L, "정규화 기초", "DB", 2, 30);

        AiSchedulePlanResponseDto response = new AiSchedulePlanResponseDto(
                List.of(new DailyPlanDto(monday, List.of())), // Tier 1 무효
                List.of(1L) // Tier 2 유효
        );
        given(chatClient.prompt().user(anyString()).call().entity(AiSchedulePlanResponseDto.class))
                .willReturn(response);

        // When & Then: maxHorizonDays=0 이고 월요일이 비가용일인 상황 -> daysWalked > 0 으로 SCHEDULE_GENERATION_FAILED 예외 발생
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.generateSchedule(
                goal(), List.of(task1), monday, Set.of(DayOfWeek.WEDNESDAY), 60, 0
        )).isInstanceOf(com.steadyteller.backend.global.exception.CustomException.class);
    }

    private LearningTask task(Long id, String title, String category, int difficulty, int minutes) {
        LearningTask task = LearningTask.builder()
                .goalId(1L)
                .title(title)
                .category(category)
                .subject("주제")
                .importance(difficulty)
                .difficulty(difficulty)
                .allocatedMinutes(minutes)
                .source(LearningTaskSource.AI_GENERATED)
                .isModified(false)
                .build();
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    private MemberGoal goal() {
        MemberGoal goal = MemberGoal.builder()
                .memberId(1L)
                .title("정보처리기사 합격하기")
                .startDate(LocalDate.of(2026, 8, 24))
                .targetDate(LocalDate.of(2026, 11, 1))
                .currentLevel("초급")
                .dailyStudyHours(1)
                .availableDays(List.of("MON", "WED"))
                .focusArea("데이터베이스 정규화")
                .build();
        ReflectionTestUtils.setField(goal, "id", 1L);
        return goal;
    }
}
