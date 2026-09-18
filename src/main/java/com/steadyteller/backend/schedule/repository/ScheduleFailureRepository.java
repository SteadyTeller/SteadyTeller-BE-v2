package com.steadyteller.backend.schedule.repository;

import com.steadyteller.backend.schedule.entity.ScheduleFailure;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleFailureRepository extends JpaRepository<ScheduleFailure, Long> {
    long countByLearningTaskId(Long learningTaskId);
    void deleteByLearningTaskId(Long learningTaskId);
    void deleteByLearningTaskIdIn(java.util.List<Long> learningTaskIds);
}
// Added for orphaned cleanup
//    void deleteByLearningTaskId(Long learningTaskId);
//    void deleteByLearningTaskIdIn(java.util.List<Long> learningTaskIds);
