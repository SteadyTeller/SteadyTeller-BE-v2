package com.steadyteller.backend.learningtask.entity;

public enum LearningTaskStatus {
    PENDING,       // 대기 (스케줄 미배정 상태)
    SCHEDULED,     // 스케줄 배정 완료
    IN_PROGRESS,   // 학습 진행 중
    FINISHED       // 학습 완료
}
