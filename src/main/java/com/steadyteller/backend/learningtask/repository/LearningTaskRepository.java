package com.steadyteller.backend.learningtask.repository;

import com.steadyteller.backend.learningtask.entity.LearningTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningTaskRepository extends JpaRepository<LearningTask, Long> {
}
