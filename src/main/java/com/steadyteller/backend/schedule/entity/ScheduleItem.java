package com.steadyteller.backend.schedule.entity;

import com.steadyteller.backend.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Schedule 내에서 특정 날짜에 배정된 개별 학습 태스크 수행 항목 (설계 명세 4번).
 * learningTaskId는 다른 도메인(LearningTask)의 PK를 그대로 저장하며, 연관관계(FK 매핑)를 맺지 않는다.
 * order(orderIndex)는 이 단계(스케줄 생성)에서 최종 확정된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "schedule_item")
public class ScheduleItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(nullable = false)
    private Long learningTaskId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private int allocatedMinutes;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleItemStatus status;

    @Builder
    private ScheduleItem(Schedule schedule, Long learningTaskId, String title, LocalDate date,
                          DayOfWeek dayOfWeek, int allocatedMinutes, int orderIndex) {
        this.schedule = schedule;
        this.learningTaskId = learningTaskId;
        this.title = title;
        this.date = date;
        this.dayOfWeek = dayOfWeek;
        this.allocatedMinutes = allocatedMinutes;
        this.orderIndex = orderIndex;
        this.status = ScheduleItemStatus.PENDING;
    }

    public static ScheduleItem create(Schedule schedule, Long learningTaskId, String title, LocalDate date,
                                       DayOfWeek dayOfWeek, int allocatedMinutes, int orderIndex) {
        return ScheduleItem.builder()
                .schedule(schedule)
                .learningTaskId(learningTaskId)
                .title(title)
                .date(date)
                .dayOfWeek(dayOfWeek)
                .allocatedMinutes(allocatedMinutes)
                .orderIndex(orderIndex)
                .build();
    }
}
