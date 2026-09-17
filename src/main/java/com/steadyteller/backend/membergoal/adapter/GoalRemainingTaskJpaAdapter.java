package com.steadyteller.backend.membergoal.adapter;

import com.steadyteller.backend.learningtask.entity.LearningTaskStatus;
import com.steadyteller.backend.membergoal.dto.GoalDeadlineResponse.RemainingTask;
import com.steadyteller.backend.membergoal.port.GoalRemainingTaskQueryPort;
import com.steadyteller.backend.schedule.entity.ScheduleItemStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 기존 태스크/일정 상태를 읽기만 한다. 제외·분할 상태 도입 시 담당자와 필터를 맞춘다. */
@Repository
@RequiredArgsConstructor
public class GoalRemainingTaskJpaAdapter implements GoalRemainingTaskQueryPort {
    private final EntityManager entityManager;

    @Override
    public List<RemainingTask> findRemainingTasks(Long goalId) {
        return entityManager.createQuery("""
                SELECT t.id, t.title FROM LearningTask t
                WHERE t.goalId = :goalId AND t.status <> :taskFinished
                AND (
                    NOT EXISTS (SELECT i.id FROM ScheduleItem i WHERE i.learningTaskId = t.id)
                    OR EXISTS (SELECT i.id FROM ScheduleItem i
                               WHERE i.learningTaskId = t.id AND i.status <> :itemFinished)
                )
                ORDER BY t.id
                """, Object[].class)
                .setParameter("goalId", goalId)
                .setParameter("taskFinished", LearningTaskStatus.FINISHED)
                .setParameter("itemFinished", ScheduleItemStatus.FINISHED)
                .getResultList().stream()
                .map(row -> new RemainingTask((Long) row[0], (String) row[1]))
                .toList();
    }
}
