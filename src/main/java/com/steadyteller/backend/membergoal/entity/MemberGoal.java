package com.steadyteller.backend.membergoal.entity;

import com.steadyteller.backend.global.common.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member_goal")
public class MemberGoal extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long memberId;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private LocalDate startDate;
    @Column(nullable = false) private LocalDate targetDate;
    @Column(nullable = false) private String currentLevel;
    @ElementCollection
    @CollectionTable(name = "member_goal_must_study_topics", joinColumns = @JoinColumn(name = "member_goal_id"))
    @Column(name = "topic", nullable = false)
    private List<String> mustStudyTopics = new ArrayList<>();
    @Enumerated(EnumType.STRING) @Column(nullable = false) private GoalStatus status = GoalStatus.IN_PROGRESS;
    @Column(nullable = false) private boolean taskGenerationLocked;

    @Builder
    public MemberGoal(Long memberId, String title, LocalDate startDate, LocalDate targetDate,
                      String currentLevel, List<String> mustStudyTopics) {
        this.memberId = memberId; this.title = title; this.startDate = startDate; this.targetDate = targetDate;
        this.currentLevel = currentLevel; this.mustStudyTopics = new ArrayList<>(mustStudyTopics);
    }
    public void update(String title, LocalDate startDate, LocalDate targetDate, String currentLevel,
                       List<String> mustStudyTopics) {
        this.title = title; this.startDate = startDate; this.targetDate = targetDate; this.currentLevel = currentLevel;
        this.mustStudyTopics = new ArrayList<>(mustStudyTopics);
    }
    public boolean isOwnedBy(Long memberId) { return this.memberId.equals(memberId); }
    public void lockTaskGeneration() { this.taskGenerationLocked = true; }
}
