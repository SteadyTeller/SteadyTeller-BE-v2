package com.steadyteller.backend.studytimer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TimerResultRepository extends JpaRepository<TimerResult, Long> {
    Optional<TimerResult> findByAttemptId(UUID attemptId);
    List<TimerResult> findAllByMemberIdAndScheduleIdAndScheduleItemIdOrderByCreatedAtDesc(
        Long memberId, Long scheduleId, Long scheduleItemId);
}
