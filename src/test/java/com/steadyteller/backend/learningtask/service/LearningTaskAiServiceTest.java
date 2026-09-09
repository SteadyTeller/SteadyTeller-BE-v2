package com.steadyteller.backend.learningtask.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.dto.AiGeneratedTaskDto;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;

@ExtendWith(MockitoExtension.class)
class LearningTaskAiServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Test
    void generateTasksRejectsEmptyFields() {
        // AI가 제목, 분류, 주제 중 하나라도 빈 값으로 반환하면 결과를 사용하지 않는다.
        LearningTaskAiService service = new LearningTaskAiService(chatClient);
        given(chatClient.prompt().user(anyString()).call()
                .entity(any(ParameterizedTypeReference.class)))
                .willReturn(List.of(new AiGeneratedTaskDto("", "category", "subject", 3, 30)));

        assertThatThrownBy(() -> service.generateTasks(goal()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void generateTasksRejectsDifficultyOutsideRangeAndNonPositiveTime() {
        // 난이도는 1~5, 예상 시간은 양수여야 한다.
        LearningTaskAiService service = new LearningTaskAiService(chatClient);
        given(chatClient.prompt().user(anyString()).call()
                .entity(any(ParameterizedTypeReference.class)))
                .willReturn(List.of(new AiGeneratedTaskDto("title", "category", "subject", 6, 0)));

        assertThatThrownBy(() -> service.generateTasks(goal()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void generateTasksWrapsAiClientFailure() {
        LearningTaskAiService service = new LearningTaskAiService(chatClient);
        given(chatClient.prompt().user(anyString()).call()
                .entity(any(ParameterizedTypeReference.class)))
                .willThrow(new RuntimeException("AI timeout"));

        assertThatThrownBy(() -> service.generateTasks(goal()))
                .isInstanceOf(CustomException.class);
    }

    private MemberGoal goal() {
        return MemberGoal.builder()
                .memberId(1L).title("Java").startDate(LocalDate.of(2026, 2, 1))
                .targetDate(LocalDate.of(2026, 2, 2)).currentLevel("BEGINNER")
                .dailyStudyHours(1).availableDays(List.of("MON")).focusArea("backend").build();
    }
}
