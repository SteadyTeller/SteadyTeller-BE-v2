package com.steadyteller.backend.schedule.entity;

import com.steadyteller.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 확정된 LearningTask들을 실제 수행 날짜에 배정한 결과의 상위 단위 (설계 명세 4번).
 * memberId/goalId는 다른 도메인(Member/MemberGoal)의 PK를 그대로 저장하며, 연관관계(FK 매핑)를 맺지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "schedule")
public class Schedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long goalId;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Builder
    private Schedule(Long memberId, Long goalId, LocalDate startDate, LocalDate endDate) {
        this.memberId = memberId;
        this.goalId = goalId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static Schedule create(Long memberId, Long goalId, LocalDate startDate, LocalDate endDate) {
        return Schedule.builder()
                .memberId(memberId)
                .goalId(goalId)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }
}
