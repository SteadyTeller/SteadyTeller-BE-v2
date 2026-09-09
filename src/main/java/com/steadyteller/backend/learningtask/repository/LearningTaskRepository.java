package com.steadyteller.backend.learningtask.repository;

import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningTaskRepository extends JpaRepository<LearningTask, Long> {

    List<LearningTask> findByGoalIdAndStatus(Long goalId, LearningTaskStatus status);
}
