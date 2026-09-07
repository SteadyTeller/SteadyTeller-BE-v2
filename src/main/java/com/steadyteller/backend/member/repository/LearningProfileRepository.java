package com.steadyteller.backend.member.repository;

import com.steadyteller.backend.member.domain.LearningProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningProfileRepository extends JpaRepository<LearningProfile, Long> {

    Optional<LearningProfile> findByMemberId(Long memberId);
}
