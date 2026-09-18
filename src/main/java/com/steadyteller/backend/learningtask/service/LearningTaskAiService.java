package com.steadyteller.backend.learningtask.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.dto.AiGeneratedTaskDto;
import com.steadyteller.backend.learningtask.exception.LearningTaskErrorCode;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/** Generates editable task candidates; scheduling is a separate step. */
@Service
@Slf4j
@RequiredArgsConstructor
public class LearningTaskAiService {
    public static final int MAX_ALLOCATED_MINUTES = 1440;
    private final ChatClient chatClient;

    public List<AiGeneratedTaskDto> generateTasks(MemberGoal goal) {
        return generateTasks(goal, "가용 시간 정보가 제공되지 않았습니다.");
    }

    public List<AiGeneratedTaskDto> generateTasks(MemberGoal goal, String availabilityConstraint) {
        try {
            List<AiGeneratedTaskDto> tasks = chatClient.prompt().user(buildPrompt(goal, availabilityConstraint)).call()
                    .entity(new ParameterizedTypeReference<List<AiGeneratedTaskDto>>() { });
            if (tasks == null || tasks.isEmpty() || tasks.stream().anyMatch(this::invalid)) {
                throw new CustomException(LearningTaskErrorCode.AI_GENERATION_FAILED);
            }
            return tasks;
        } catch (CustomException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("AI task generation failed: goalId={}", goal.getId(), exception);
            throw new CustomException(LearningTaskErrorCode.AI_GENERATION_FAILED);
        }
    }

    private boolean invalid(AiGeneratedTaskDto task) {
        return task == null || blank(task.title()) || blank(task.category()) || blank(task.subject())
                || task.difficulty() == null || task.difficulty() < 1 || task.difficulty() > 5
                || task.allocatedMinutes() == null || task.allocatedMinutes() <= 0
                || task.allocatedMinutes() > MAX_ALLOCATED_MINUTES;
    }

    private boolean blank(String value) { return Objects.isNull(value) || value.isBlank(); }

    private String buildPrompt(MemberGoal goal, String availabilityConstraint) {
        return """
                당신은 한국인 학습자를 위한 전문 학습 코치입니다. 사용자가 검토하고 수정할 수 있는 학습 태스크 목록을 생성하세요.

                [학습 목표]
                - 목표명: %s
                - 시작일: %s
                - 목표일: %s
                - 현재 수준: %s
                - 꼭 하고 싶은 공부: %s

                [응답 규칙]
                - 반드시 JSON 배열로 응답하세요.
                - 각 항목에는 title, category, subject, difficulty(1~5), allocatedMinutes 필드를 포함하세요.
                - title, category, subject는 모두 자연스러운 한국어로 작성하세요.
                - '꼭 하고 싶은 공부' 항목을 우선 반영하세요.
                - 태스크는 이후 여러 일정 슬롯으로 분할될 수 있습니다.
                - 태스크들의 allocatedMinutes 합계는 아래 가용 시간 제약을 넘지 않아야 합니다.
                """.formatted(goal.getTitle(), goal.getStartDate(), goal.getTargetDate(), goal.getCurrentLevel(),
                goal.getMustStudyTopics()) + "\n[가용 시간 제약]\n" + availabilityConstraint;
    }
}
