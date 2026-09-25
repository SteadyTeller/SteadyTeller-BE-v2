package com.steadyteller.backend.replan.repository;
import com.steadyteller.backend.replan.entity.ReplanTaskCandidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReplanTaskCandidateRepository extends JpaRepository<ReplanTaskCandidate, Long> {
    List<ReplanTaskCandidate> findByProposalIdOrderByIdAsc(Long proposalId);
    void deleteByProposalId(Long proposalId);
}
