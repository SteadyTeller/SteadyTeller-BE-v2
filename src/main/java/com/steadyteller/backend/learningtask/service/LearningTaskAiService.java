package com.steadyteller.backend.learningtask.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.learningtask.dto.AiGeneratedTaskDto;
import com.steadyteller.backend.learningtask.exception.LearningTaskErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/**
 * MemberGoal 정보를 컨텍스트로 AI에게 세부 학습 태스크 생성을 요청한다 (설계 명세 2번).
 * 여기서 만들어진 결과는 아직 DB에 저장되지 않는 "1차 생성" 결과이며,
 * LearningTaskService가 이를 후보(candidate)로 감싸서 캐시에 보관한다.
 */
@Service
@RequiredArgsConstructor
public class LearningTaskAiService {

    private final ChatClient chatClient;

    public List<AiGeneratedTaskDto> generateTasks(MemberGoal goal) {
        List<AiGeneratedTaskDto> tasks = chatClient.prompt()
                .user(buildPrompt(goal))
                .call()
                .entity(new ParameterizedTypeReference<List<AiGeneratedTaskDto>>() {
                });

        if (tasks == null || tasks.isEmpty()) {
            throw new CustomException(LearningTaskErrorCode.AI_GENERATION_FAILED);
        }
        return tasks;
    }

    private String buildPrompt(MemberGoal goal) {
        return """
                당신은 학습 코치입니다. 아래 학습 목표를 바탕으로 사용자가 바로 수행할 수 있는
                세부 학습 태스크 목록을 생성하세요.

                [학습 목표]
                - 목표명: %s
                - 시작일: %s
                - 목표 달성일: %s
                - 현재 수준: %s
                - 일일 가용 학습 시간: %d시간
                - 가용 요일: %s
                - 집중 학습 분야: %s

                [생성 규칙]
                - title: 태스크명
                - category: 자유 텍스트 대분류 (예: "데이터베이스")
                - subject: 자유 텍스트 세부 주제 (예: "정규화")
                - difficulty: 난이도, 1(매우 쉬움) ~ 5(매우 어려움) 사이의 정수
                - allocatedMinutes: 예상 소요 시간(분), 일일 가용 학습 시간을 고려한 현실적인 값
                - 최대한 빠지는 거 없이 해당 태스크를 수행하는데 필요한 항목들을 다 넣어주세요.
                - orderIndex 등 수행 순서는 이 단계에서 정하지 마세요.
                """.formatted(
                goal.getTitle(),
                goal.getStartDate(),
                goal.getTargetDate(),
                goal.getCurrentLevel(),
                goal.getDailyStudyHours(),
                goal.getAvailableDays(),
                goal.getFocusArea()
        );
    }
}
