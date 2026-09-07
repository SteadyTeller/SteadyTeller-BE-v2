package com.steadyteller.backend.membergoal.repository;

import com.steadyteller.backend.membergoal.entity.MemberGoal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberGoalRepository extends JpaRepository<MemberGoal, Long> {

    List<MemberGoal> findByMemberId(Long memberId);
}
