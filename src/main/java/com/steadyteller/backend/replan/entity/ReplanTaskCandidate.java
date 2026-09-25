package com.steadyteller.backend.replan.entity;

import com.steadyteller.backend.global.common.BaseTimeEntity;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "replan_task_candidate")
public class ReplanTaskCandidate extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long proposalId;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private String category;
    @Column(nullable = false) private String subject;
    @Column(nullable = false) private Integer difficulty;
    @Column(nullable = false) private Integer allocatedMinutes;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private LearningTaskSource source;
    @Column(nullable = false) private boolean modified;

    private ReplanTaskCandidate(Long proposalId, String title, String category, String subject, Integer difficulty,
                                Integer allocatedMinutes, LearningTaskSource source, boolean modified) {
        this.proposalId = proposalId; this.title = title; this.category = category; this.subject = subject;
        this.difficulty = difficulty; this.allocatedMinutes = allocatedMinutes; this.source = source; this.modified = modified;
    }
    public static ReplanTaskCandidate create(Long proposalId, String title, String category, String subject,
                                             Integer difficulty, Integer allocatedMinutes, LearningTaskSource source) {
        return new ReplanTaskCandidate(proposalId, title, category, subject, difficulty, allocatedMinutes, source, false);
    }
    public void update(String title, String category, String subject, Integer difficulty, Integer allocatedMinutes) {
        this.title = title; this.category = category; this.subject = subject; this.difficulty = difficulty;
        this.allocatedMinutes = allocatedMinutes; this.modified = true;
    }
}
