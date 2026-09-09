package com.steadyteller.backend.learningtask.repository;

import com.steadyteller.backend.learningtask.candidate.LearningTaskCandidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningTaskCandidateRepository extends JpaRepository<LearningTaskCandidate, Long> {

    List<LearningTaskCandidate> findByGoalIdOrderByIdAsc(Long goalId);

    void deleteByGoalId(Long goalId);
}
