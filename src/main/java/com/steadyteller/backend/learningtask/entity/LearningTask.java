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
 * 승인 전까지는 이 엔티티로 저장되지 않고, 후보 상태로 별도 저장소(#1/PR #6의 LearningTaskCandidateStore)에만 존재한다.
 * category/subject는 마스터 테이블/FK가 아닌 AI가 생성하는 자유 텍스트다.
 *
 * 스케줄링(#7)에 필요한 최소 범위만 정의한다 (조회 전용).
 * 후보 생성/검토/승인(confirm) 로직은 #1(PR #6)에서 별도로 진행 중이며,
 * 해당 PR이 dev에 머지되면 이 클래스는 정의가 중복되므로 병합 시점에 정리가 필요하다.
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
}
