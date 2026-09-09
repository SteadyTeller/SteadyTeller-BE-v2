package com.steadyteller.backend.membergoal.repository;

import com.steadyteller.backend.membergoal.entity.MemberGoal;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberGoalRepository extends JpaRepository<MemberGoal, Long> {

    List<MemberGoal> findByMemberId(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM MemberGoal g WHERE g.id = :id")
    Optional<MemberGoal> findByIdForUpdate(@Param("id") Long id);
}
