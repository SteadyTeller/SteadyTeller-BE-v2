package com.steadyteller.backend.replan.repository;
import com.steadyteller.backend.replan.entity.ReplanAvailability;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReplanAvailabilityRepository extends JpaRepository<ReplanAvailability, Long> {
    List<ReplanAvailability> findByProposalIdOrderByDayOfWeekAscStartTimeAsc(Long proposalId);
    void deleteByProposalId(Long proposalId);
}
