package com.steadyteller.backend.learningtask.repository;

import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearningTaskRepository extends JpaRepository<LearningTask, Long> {

    List<LearningTask> findByGoalIdAndStatus(Long goalId, LearningTaskStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lt FROM LearningTask lt WHERE lt.goalId = :goalId AND lt.status = :status")
    List<LearningTask> findByGoalIdAndStatusForUpdate(@Param("goalId") Long goalId, @Param("status") LearningTaskStatus status);
}
