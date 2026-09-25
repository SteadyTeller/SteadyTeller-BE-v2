package com.steadyteller.backend.learningtask.service;
import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.dto.AiGeneratedTaskDto;
import com.steadyteller.backend.learningtask.exception.LearningTaskErrorCode;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import java.util.*;
import lombok.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class LearningTaskAiService {
 public static final int MAX_ALLOCATED_MINUTES=1440; private final ChatClient chatClient;
 public List<AiGeneratedTaskDto> generateTasks(MemberGoal goal){return generateTasks(goal,"가용시간 정보 없음");}
 public List<AiGeneratedTaskDto> generateTasks(MemberGoal goal,String constraint){try{List<AiGeneratedTaskDto> tasks=chatClient.prompt().user(buildPrompt(goal,constraint)).call().entity(new ParameterizedTypeReference<List<AiGeneratedTaskDto>>(){});if(tasks==null||tasks.isEmpty()||tasks.stream().anyMatch(this::invalid))throw new CustomException(LearningTaskErrorCode.AI_GENERATION_FAILED);return tasks;}catch(CustomException e){throw e;}catch(RuntimeException e){throw new CustomException(LearningTaskErrorCode.AI_GENERATION_FAILED);}}
 private boolean invalid(AiGeneratedTaskDto t){return t==null||blank(t.title())||blank(t.category())||blank(t.subject())||t.difficulty()==null||t.difficulty()<1||t.difficulty()>5||t.allocatedMinutes()==null||t.allocatedMinutes()<=0||t.allocatedMinutes()>MAX_ALLOCATED_MINUTES;}
 private boolean blank(String s){return s==null||s.isBlank();}
 private String buildPrompt(MemberGoal goal,String constraint){return """
당신은 한국인 학습자를 위한 학습 코치입니다. 반드시 한국어로 JSON 배열만 응답하세요.
[학습 목표] %s
[기간] %s ~ %s
[현재 수준] %s
[꼭 하고 싶은 공부] %s
[가용시간] %s
각 항목은 title, category, subject, difficulty(1~5), allocatedMinutes를 포함합니다.
전체 allocatedMinutes 합은 가용시간을 넘지 마세요.
""".formatted(goal.getTitle(),goal.getStartDate(),goal.getTargetDate(),goal.getCurrentLevel(),goal.getMustStudyTopics(),constraint);}
}
