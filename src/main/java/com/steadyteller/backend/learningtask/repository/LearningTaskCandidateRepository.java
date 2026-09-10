package com.steadyteller.backend.learningtask.repository;

import com.steadyteller.backend.learningtask.candidate.LearningTaskCandidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearningTaskCandidateRepository extends JpaRepository<LearningTaskCandidate, Long> {

    List<LearningTaskCandidate> findByGoalIdOrderByIdAsc(Long goalId);

    @Modifying
    @Query("DELETE FROM LearningTaskCandidate c WHERE c.goalId = :goalId")
    void deleteByGoalId(@Param("goalId") Long goalId);
}
