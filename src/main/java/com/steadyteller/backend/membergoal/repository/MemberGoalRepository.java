package com.steadyteller.backend.membergoal.repository;

import com.steadyteller.backend.membergoal.entity.MemberGoal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberGoalRepository extends JpaRepository<MemberGoal, Long> {
}
