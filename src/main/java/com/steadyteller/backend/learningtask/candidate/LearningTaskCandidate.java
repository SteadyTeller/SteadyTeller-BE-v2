package com.steadyteller.backend.learningtask.candidate;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검토(승인) 전 단계의 후보 태스크.
 * <p>
 * 승인 전까지는 LearningTask로 저장하지 않는다는 설계 원칙에 따라, 후보는 별도 테이블(learning_task_candidate)에서
 * 관리하고 confirm 호출 시점에만 LearningTask로 일괄 이관한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "learning_task_candidate")
public class LearningTaskCandidate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long goalId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private Integer difficulty;

    @Column(nullable = false)
    private Integer allocatedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LearningTaskSource source;

    @Column(nullable = false)
    private boolean modified;

    @Builder
    public LearningTaskCandidate(Long goalId, Long memberId, String title, String category, String subject,
                                  Integer difficulty, Integer allocatedMinutes, LearningTaskSource source,
                                  boolean modified) {
        this.goalId = goalId;
        this.memberId = memberId;
        this.title = title;
        this.category = category;
        this.subject = subject;
        this.difficulty = difficulty;
        this.allocatedMinutes = allocatedMinutes;
        this.source = source;
        this.modified = modified;
    }

    // importance는 프론트 표시 전용 값으로, difficulty와 동일한 값을 사용한다.
    public Integer getImportance() {
        return difficulty;
    }

    public void update(String title, String category, String subject, Integer difficulty, Integer allocatedMinutes) {
        this.title = title;
        this.category = category;
        this.subject = subject;
        this.difficulty = difficulty;
        this.allocatedMinutes = allocatedMinutes;
        this.modified = true;
    }
}
