package com.steadyteller.backend.learningtask.dto;

import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.learningtask.entity.LearningTaskStatus;
import java.time.LocalDateTime;
import java.util.List;

public record LearningTaskResponseDto(
        Long id,
        Long goalId,
        String title,
        String category,
        String subject,
        Integer difficulty,
        Integer allocatedMinutes,
        LearningTaskStatus status,
        LearningTaskSource source,
        boolean isModified,
        List<TaskSchedulePlacementResponseDto> scheduleItems,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static LearningTaskResponseDto from(LearningTask task, List<TaskSchedulePlacementResponseDto> scheduleItems) {
        return new LearningTaskResponseDto(
                task.getId(),
                task.getGoalId(),
                task.getTitle(),
                task.getCategory(),
                task.getSubject(),
                task.getDifficulty(),
                task.getAllocatedMinutes(),
                task.getStatus(),
                task.getSource(),
                task.isModified(),
                scheduleItems,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    public static LearningTaskResponseDto from(LearningTask task) {
        return from(task, List.of());
    }
}
