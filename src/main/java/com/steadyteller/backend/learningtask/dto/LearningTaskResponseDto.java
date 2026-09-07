package com.steadyteller.backend.learningtask.dto;

import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.learningtask.entity.LearningTaskStatus;
import java.time.LocalDateTime;

public record LearningTaskResponseDto(
        Long id,
        Long goalId,
        String title,
        String category,
        String subject,
        Integer importance,
        Integer difficulty,
        Integer allocatedMinutes,
        LearningTaskStatus status,
        LearningTaskSource source,
        boolean isModified,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static LearningTaskResponseDto from(LearningTask task) {
        return new LearningTaskResponseDto(
                task.getId(),
                task.getGoalId(),
                task.getTitle(),
                task.getCategory(),
                task.getSubject(),
                task.getImportance(),
                task.getDifficulty(),
                task.getAllocatedMinutes(),
                task.getStatus(),
                task.getSource(),
                task.isModified(),
                task.getReviewedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
