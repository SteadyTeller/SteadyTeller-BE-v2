package com.steadyteller.backend.learningtask.entity;

import com.steadyteller.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검토 승인을 거쳐 최종 확정된 학습 태스크 (설계 명세 3번).
 * 승인 전까지는 이 엔티티로 저장되지 않고, 후보 상태로 in-memory 캐시(LearningTaskCandidateStore)에만 존재한다.
 * category/subject는 마스터 테이블/FK가 아닌 AI가 생성하는 자유 텍스트다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "learning_task")
public class LearningTask extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long goalId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String subject;

    // 프론트 표시 전용 값. difficulty를 기준으로 산정되며 스케줄링 로직에는 직접 반영되지 않는다.
    @Column(nullable = false)
    private Integer importance;

    @Column(nullable = false)
    private Integer difficulty;

    @Column(nullable = false)
    private Integer allocatedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LearningTaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LearningTaskSource source;

    @Column(nullable = false)
    private boolean isModified;

    @Column(nullable = false)
    private LocalDateTime reviewedAt;

    @Builder
    public LearningTask(Long goalId, String title, String category, String subject, Integer importance,
                         Integer difficulty, Integer allocatedMinutes, LearningTaskSource source, boolean isModified) {
        this.goalId = goalId;
        this.title = title;
        this.category = category;
        this.subject = subject;
        this.importance = importance;
        this.difficulty = difficulty;
        this.allocatedMinutes = allocatedMinutes;
        this.status = LearningTaskStatus.PENDING;
        this.source = source;
        this.isModified = isModified;
        this.reviewedAt = LocalDateTime.now();
    }

    public void markAsScheduled() {
        this.status = LearningTaskStatus.SCHEDULED;
    }

    public void markAsPending() {
        this.status = LearningTaskStatus.PENDING;
    }
}
