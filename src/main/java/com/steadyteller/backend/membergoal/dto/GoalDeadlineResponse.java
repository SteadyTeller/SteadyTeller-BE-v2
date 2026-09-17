package com.steadyteller.backend.membergoal.dto;

import java.time.LocalDate;
import java.util.List;

/** 목표일 확인용 조회 결과. 재배치 후 예상 종료일은 별도 계약으로 연결한다. */
public record GoalDeadlineResponse(
        Long goalId,
        LocalDate targetDate,
        boolean deadlineReached,
        List<RemainingTask> remainingTasks
) {
    public record RemainingTask(Long taskId, String title) { }
}
