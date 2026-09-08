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
