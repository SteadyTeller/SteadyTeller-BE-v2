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
        return generateTasks(goal, "No availability constraint was provided.");
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
                You are an expert learning coach. Generate an editable list of learning tasks.
                Goal: %s
                Start date: %s
                Target date: %s
                Current level: %s
                Required study topics: %s

                Return JSON task objects with title, category, subject, difficulty (1-5), and allocatedMinutes.
                Required study topics must be prioritized. Keep each task small enough for one contiguous availability window.
                """.formatted(goal.getTitle(), goal.getStartDate(), goal.getTargetDate(), goal.getCurrentLevel(),
                goal.getMustStudyTopics()) + "\nAvailability constraint:\n" + availabilityConstraint;
    }
}
