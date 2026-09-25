package com.steadyteller.backend.replan.dto;

import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.replan.entity.ReplanTaskCandidate;
import java.time.LocalDateTime;

public record ReplanTaskCandidateResponseDto(Long id, String title, String category, String subject,
                                             Integer difficulty, Integer allocatedMinutes,
                                             LearningTaskSource source, boolean modified,
                                             LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static ReplanTaskCandidateResponseDto from(ReplanTaskCandidate candidate) {
        return new ReplanTaskCandidateResponseDto(candidate.getId(), candidate.getTitle(), candidate.getCategory(),
                candidate.getSubject(), candidate.getDifficulty(), candidate.getAllocatedMinutes(), candidate.getSource(),
                candidate.isModified(), candidate.getCreatedAt(), candidate.getUpdatedAt());
    }
}
