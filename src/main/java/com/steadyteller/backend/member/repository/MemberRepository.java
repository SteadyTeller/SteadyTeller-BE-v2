package com.steadyteller.backend.member.repository;

import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.domain.MemberStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    Optional<Member> findByEmailAndStatus(String email, MemberStatus status);

    Optional<Member> findByIdAndStatus(Long id, MemberStatus status);
}
