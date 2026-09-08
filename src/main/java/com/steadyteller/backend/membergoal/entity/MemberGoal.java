package com.steadyteller.backend.membergoal.entity;

import com.steadyteller.backend.global.common.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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

/**
 * 회원의 학습 목표. 한 회원이 여러 개의 MemberGoal을 가질 수 있다 (1:N).
 * memberId는 JWT에서 추출한 값을 그대로 저장하며, Member 엔티티는 별도 도메인(다른 팀원 구현)이므로
 * 이 프로젝트 내에서는 연관관계(FK 매핑) 없이 순수 컬럼으로만 참조한다.
 *
 * 스케줄링(#7)에 필요한 최소 범위만 정의한다 (조회 + 소유권 검증).
 * 생성/수정/삭제 API 및 AI 세부 태스크 생성은 #1(PR #6)에서 별도로 진행 중이며,
 * 해당 PR이 dev에 머지되면 이 클래스는 정의가 중복되므로 병합 시점에 정리가 필요하다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member_goal")
public class MemberGoal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate targetDate;

    @Column(nullable = false)
    private String currentLevel;

    @Column(nullable = false)
    private Integer dailyStudyHours;

    @ElementCollection
    @CollectionTable(name = "member_goal_available_days", joinColumns = @JoinColumn(name = "member_goal_id"))
    @Column(name = "day_of_week", nullable = false)
    private List<String> availableDays = new ArrayList<>();

    @Column(nullable = false)
    private String focusArea;

    @Builder
    public MemberGoal(Long memberId, String title, LocalDate startDate, LocalDate targetDate,
                       String currentLevel, Integer dailyStudyHours, List<String> availableDays, String focusArea) {
        this.memberId = memberId;
        this.title = title;
        this.startDate = startDate;
        this.targetDate = targetDate;
        this.currentLevel = currentLevel;
        this.dailyStudyHours = dailyStudyHours;
        this.availableDays = availableDays;
        this.focusArea = focusArea;
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }
}
