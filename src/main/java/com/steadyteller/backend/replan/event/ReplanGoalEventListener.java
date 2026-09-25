package com.steadyteller.backend.replan.event;

import com.steadyteller.backend.membergoal.event.MemberGoalDeletedEvent;
import com.steadyteller.backend.replan.repository.ReplanAvailabilityRepository;
import com.steadyteller.backend.replan.repository.ReplanProposalRepository;
import com.steadyteller.backend.replan.repository.ReplanTaskCandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReplanGoalEventListener {
    private final ReplanProposalRepository proposalRepository;
    private final ReplanAvailabilityRepository availabilityRepository;
    private final ReplanTaskCandidateRepository candidateRepository;

    @EventListener
    @Transactional
    public void onGoalDeleted(MemberGoalDeletedEvent event) {
        proposalRepository.findByGoalId(event.goalId()).ifPresent(proposal -> {
            candidateRepository.deleteByProposalId(proposal.getId());
            availabilityRepository.deleteByProposalId(proposal.getId());
            proposalRepository.delete(proposal);
        });
    }
}
