package com.steadyteller.backend.learningtask.dto;
import com.steadyteller.backend.learningtask.entity.*;
import java.time.LocalDateTime;
import java.util.*;
public record LearningTaskResponseDto(Long id,Long goalId,String title,String category,String subject,Integer difficulty,Integer allocatedMinutes,LearningTaskStatus status,LearningTaskSource source,boolean isModified,List<TaskSchedulePlacementResponseDto> scheduleItems,LocalDateTime createdAt,LocalDateTime updatedAt){
 public static LearningTaskResponseDto from(LearningTask t,List<TaskSchedulePlacementResponseDto> items){return new LearningTaskResponseDto(t.getId(),t.getGoalId(),t.getTitle(),t.getCategory(),t.getSubject(),t.getDifficulty(),t.getAllocatedMinutes(),t.getStatus(),t.getSource(),t.isModified(),items,t.getCreatedAt(),t.getUpdatedAt());}
 public static LearningTaskResponseDto from(LearningTask t){return from(t,List.of());}
}
