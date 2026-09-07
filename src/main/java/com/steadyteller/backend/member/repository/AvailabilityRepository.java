package com.steadyteller.backend.member.repository;

import com.steadyteller.backend.member.domain.Availability;
import java.util.List;
import java.util.Optional;
import java.time.DayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {

    List<Availability> findAllByMemberIdOrderByDayOfWeekAscStartTimeAsc(Long memberId);

    List<Availability> findAllByMemberIdAndDayOfWeek(Long memberId, DayOfWeek dayOfWeek);

    Optional<Availability> findByIdAndMemberId(Long id, Long memberId);
}
