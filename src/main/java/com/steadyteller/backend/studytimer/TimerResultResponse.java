package com.steadyteller.backend.studytimer;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 타이머 수행 결과의 외부 응답 형식이다.
 *
 * <p>JPA 엔티티를 API에 직접 노출하지 않아 저장 구조 변경이 응답 계약에 영향을 주지 않도록 한다.</p>
 */
public record TimerResultResponse(
        Long id,
        UUID attemptId,
        Long scheduleId,
        Long scheduleItemId,
        Long goalId,
        Long learningTaskId,
        String title,
        int plannedMinutes,
        int actualMinutes,
        StudyResult result,
        String reasonCode,
        String reasonDetail,
        String learnedContent,
        String remainingContent,
        LocalDateTime createdAt
) {
    public static TimerResultResponse from(TimerResult result) {
        return new TimerResultResponse(
                result.getId(),
                result.getAttemptId(),
                result.getScheduleId(),
                result.getScheduleItemId(),
                result.getGoalId(),
                result.getLearningTaskId(),
                result.getTitle(),
                result.getPlannedMinutes(),
                result.getActualMinutes(),
                result.getResult(),
                result.getReasonCode(),
                result.getReasonDetail(),
                result.getLearnedContent(),
                result.getRemainingContent(),
                result.getCreatedAt()
        );
    }
}
