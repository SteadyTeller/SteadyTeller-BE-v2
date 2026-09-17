package com.steadyteller.backend.schedule.entity;

import com.steadyteller.backend.global.common.BaseTimeEntity;
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
@Table(name = "schedule_failure")
public class ScheduleFailure extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long scheduleItemId;
    @Column(nullable = false) private Long learningTaskId;
    @Column(nullable = false, length = 50) private String reasonCode;
    @Column(length = 1000) private String reasonDetail;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private FailureHandlingAction action;

    private ScheduleFailure(Long itemId, Long taskId, String code, String detail, FailureHandlingAction action) {
        this.scheduleItemId = itemId; this.learningTaskId = taskId; this.reasonCode = code;
        this.reasonDetail = detail; this.action = action;
    }
    public static ScheduleFailure create(Long itemId, Long taskId, String code, String detail,
                                         FailureHandlingAction action) {
        return new ScheduleFailure(itemId, taskId, code, detail, action);
    }
}
