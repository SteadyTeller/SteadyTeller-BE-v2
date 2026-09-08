package com.steadyteller.backend.schedule.service;

import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.schedule.dto.AiTaskOrderResponseDto;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * MemberGoal + 확정된 LearningTask 목록을 컨텍스트로 AI에게 수행 순서 결정을 요청한다 (설계 명세 4번).
 * 실제 날짜/시간 배정(availableDays, dailyStudyHours 제약)은 AI가 아닌 ScheduleAllocator가 담당하며,
 * 이 서비스는 오직 "어떤 순서로 수행하면 좋을지"에 대한 힌트만 제공한다.
 *
 * AI 응답이 없거나, 확정된 태스크 id 집합과 정확히 일치하는 순열이 아니면
 * (id 누락/중복/미확정 태스크 포함 등) 신뢰하지 않고 기본 순서(난이도 오름차순)로 대체한다.
 * 스케줄 생성 자체가 AI 순서 결정 실패로 인해 막히지 않도록 하기 위함이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleAiService {

    private final ChatClient chatClient;

    public List<LearningTask> orderTasks(MemberGoal goal, List<LearningTask> confirmedTasks) {
        List<Long> validIds = confirmedTasks.stream().map(LearningTask::getId).toList();

        try {
            AiTaskOrderResponseDto response = chatClient.prompt()
                    .user(buildPrompt(goal, confirmedTasks))
                    .call()
                    .entity(AiTaskOrderResponseDto.class);
            List<Long> orderedIds = response == null ? null : response.orderedLearningTaskIds();

            if (isValidPermutation(orderedIds, validIds)) {
                Map<Long, LearningTask> byId = confirmedTasks.stream()
                        .collect(Collectors.toMap(LearningTask::getId, Function.identity()));
                return orderedIds.stream().map(byId::get).toList();
            }
            log.warn("AI 태스크 순서 응답이 유효하지 않아 기본 순서로 대체합니다. goalId={}", goal.getId());
        } catch (Exception e) {
            log.warn("AI 태스크 순서 결정에 실패해 기본 순서로 대체합니다. goalId={}, cause={}",
                    goal.getId(), e.getMessage());
        }
        return defaultOrder(confirmedTasks);
    }

    private boolean isValidPermutation(List<Long> orderedIds, List<Long> validIds) {
        return orderedIds != null
                && orderedIds.size() == validIds.size()
                && new HashSet<>(orderedIds).equals(new HashSet<>(validIds));
    }

    private List<LearningTask> defaultOrder(List<LearningTask> tasks) {
        return tasks.stream()
                .sorted(Comparator.comparing(LearningTask::getDifficulty).thenComparing(LearningTask::getId))
                .toList();
    }

    private String buildPrompt(MemberGoal goal, List<LearningTask> tasks) {
        String taskLines = tasks.stream()
                .map(task -> "- id=%d, title=%s, category=%s, subject=%s, difficulty=%d, allocatedMinutes=%d"
                        .formatted(task.getId(), task.getTitle(), task.getCategory(), task.getSubject(),
                                task.getDifficulty(), task.getAllocatedMinutes()))
                .collect(Collectors.joining("\n"));

        return """
                당신은 학습 코치입니다. 아래 학습 목표와 확정된 학습 태스크 목록을 참고하여
                사용자가 수행할 최적의 순서를 정하세요. 실제 날짜/시간 배정은 서버가 별도로 계산하므로
                당신은 순서만 결정하면 됩니다.

                [학습 목표]
                - 목표명: %s
                - 현재 수준: %s
                - 집중 학습 분야: %s

                [확정된 태스크 목록]
                %s

                [규칙]
                - orderedLearningTaskIds 배열에는 위 태스크 id를 정확히 한 번씩만 포함하세요.
                - id를 새로 만들거나 누락하지 마세요.
                - 선수 개념(난이도가 낮은 것)을 먼저, 연관된 주제(category/subject)는 가능한 이어서 배치하세요.
                """.formatted(goal.getTitle(), goal.getCurrentLevel(), goal.getFocusArea(), taskLines);
    }
}
