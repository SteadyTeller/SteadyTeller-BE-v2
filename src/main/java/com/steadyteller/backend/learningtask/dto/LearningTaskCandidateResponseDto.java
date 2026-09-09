package com.steadyteller.backend.learningtask.dto;

import com.steadyteller.backend.learningtask.candidate.LearningTaskCandidate;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;

public record LearningTaskCandidateResponseDto(
        Long candidateId,
        Long goalId,
        String title,
        String category,
        String subject,
        Integer importance,
        Integer difficulty,
        Integer allocatedMinutes,
        LearningTaskSource source,
        boolean isModified
) {
    public static LearningTaskCandidateResponseDto from(LearningTaskCandidate candidate) {
        return new LearningTaskCandidateResponseDto(
                candidate.getCandidateId(),
                candidate.getGoalId(),
                candidate.getTitle(),
                candidate.getCategory(),
                candidate.getSubject(),
                candidate.getImportance(),
                candidate.getDifficulty(),
                candidate.getAllocatedMinutes(),
                candidate.getSource(),
                candidate.isModified()
        );
    }
}
