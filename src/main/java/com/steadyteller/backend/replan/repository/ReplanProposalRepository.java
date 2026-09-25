package com.steadyteller.backend.replan.repository;
import com.steadyteller.backend.replan.entity.ReplanProposal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReplanProposalRepository extends JpaRepository<ReplanProposal, Long> {
    Optional<ReplanProposal> findByGoalId(Long goalId);
    void deleteByGoalId(Long goalId);
}
