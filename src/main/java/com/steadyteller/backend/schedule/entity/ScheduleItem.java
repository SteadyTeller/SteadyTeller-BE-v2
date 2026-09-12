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

    /**
     * 사용자가 이 항목의 수행 날짜/시간을 수동으로 재배치할 때 사용한다.
     * 학습 수행 상태(status)는 별도 단계(학습 수행)의 소관이라 여기서 다루지 않는다.
     */
    public void reschedule(LocalDate date, DayOfWeek dayOfWeek, int allocatedMinutes) {
        this.date = date;
        this.dayOfWeek = dayOfWeek;
        this.allocatedMinutes = allocatedMinutes;
    }

    public void updateOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    /**
     * 사용자가 이 항목의 학습을 시작했을 때 호출한다. 이미 FINISHED인 항목을 다시 시작 상태로 되돌리지는
     * 않는다(완료 취소는 revertCompletion의 소관) — 시작은 완료 이전 단계에서만 의미가 있는 전이다.
     */
    public void start() {
        if (this.status == ScheduleItemStatus.FINISHED) {
            return;
        }
        this.status = ScheduleItemStatus.IN_PROGRESS;
    }

    /**
     * 사용자가 이 항목의 학습을 완료했을 때 호출한다. 완료는 기본적으로 되돌릴 수 없는 단방향 전이이며,
     * 이미 FINISHED인 항목에 다시 호출해도 상태는 그대로이므로(멱등) 별도 상태 검증을 하지 않는다.
     */
    public void finish() {
        this.status = ScheduleItemStatus.FINISHED;
    }

    /**
     * 완료를 잘못 누른 경우 등 예외적으로 완료 처리를 취소할 때 호출한다.
     * PENDING/IN_PROGRESS 상태에 호출해도 결과적으로 미완료 상태이므로(멱등) 별도 상태 검증을 하지 않는다.
     */
    public void revertCompletion() {
        this.status = ScheduleItemStatus.PENDING;
    }
}
