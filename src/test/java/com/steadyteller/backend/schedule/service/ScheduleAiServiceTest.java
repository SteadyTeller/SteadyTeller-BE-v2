package com.steadyteller.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.schedule.dto.AiTaskOrderResponseDto;
import java.time.LocalDate;
import java.util.List;
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

    @Test
    void ordersTasksAccordingToValidAiPermutation() {
        LearningTask task1 = task(1L, 2);
        LearningTask task2 = task(2L, 4);
        ScheduleAiService service = new ScheduleAiService(chatClient);
        given(chatClient.prompt().user(anyString()).call().entity(AiTaskOrderResponseDto.class))
                .willReturn(new AiTaskOrderResponseDto(List.of(2L, 1L)));

        List<LearningTask> result = service.orderTasks(goal(), List.of(task1, task2));

        assertThat(result).extracting(LearningTask::getId).containsExactly(2L, 1L);
    }

    @Test
    void fallsBackToDifficultyOrderWhenAiPermutationIsInvalid() {
        // 후보에 없는 id(99)가 섞여 있으면 신뢰하지 않고 기본 순서(난이도 오름차순)로 대체한다.
        LearningTask hard = task(1L, 5);
        LearningTask easy = task(2L, 1);
        ScheduleAiService service = new ScheduleAiService(chatClient);
        given(chatClient.prompt().user(anyString()).call().entity(AiTaskOrderResponseDto.class))
                .willReturn(new AiTaskOrderResponseDto(List.of(1L, 99L)));

        List<LearningTask> result = service.orderTasks(goal(), List.of(hard, easy));

        assertThat(result).extracting(LearningTask::getId).containsExactly(2L, 1L);
    }

    @Test
    void fallsBackToDifficultyOrderWhenAiCallThrows() {
        LearningTask hard = task(1L, 5);
        LearningTask easy = task(2L, 1);
        ScheduleAiService service = new ScheduleAiService(chatClient);
        given(chatClient.prompt().user(anyString()).call().entity(AiTaskOrderResponseDto.class))
                .willThrow(new RuntimeException("AI 호출 실패"));

        List<LearningTask> result = service.orderTasks(goal(), List.of(hard, easy));

        assertThat(result).extracting(LearningTask::getId).containsExactly(2L, 1L);
    }

    private LearningTask task(Long id, int difficulty) {
        LearningTask task = LearningTask.builder()
                .goalId(1L)
                .title("title-" + id)
                .category("category")
                .subject("subject")
                .importance(difficulty)
                .difficulty(difficulty)
                .allocatedMinutes(30)
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
                .startDate(LocalDate.of(2026, 8, 19))
                .targetDate(LocalDate.of(2026, 11, 1))
                .currentLevel("초급")
                .dailyStudyHours(2)
                .availableDays(List.of("MON", "WED", "FRI"))
                .focusArea("데이터베이스 정규화")
                .build();
        ReflectionTestUtils.setField(goal, "id", 1L);
        return goal;
    }
}
