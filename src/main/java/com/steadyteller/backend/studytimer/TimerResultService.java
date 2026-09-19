package com.steadyteller.backend.studytimer;
import com.steadyteller.backend.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
@Service
@RequiredArgsConstructor
public class TimerResultService {
    private final TimerResultRepository repository;
    private final TimerScheduleQueryPort scheduleQuery;
    private static final Set<String> REASONS = Set.of("LACK_OF_TIME", "UNEXPECTED_EVENT", "DIFFICULTY_TOO_HIGH", "LACK_OF_FOCUS", "USER_DEFERRED", "OTHER");
    @Transactional
    public TimerResult save(Long memberId, Long scheduleId, Long itemId, TimerResultRequest request) {
        if (request.attemptId() == null || request.actualMinutes() == null || request.actualMinutes() < 0
            || request.actualMinutes() > 1440 || request.result() == null
            || (request.result() == StudyResult.FAILED && !REASONS.contains(request.reasonCode() == null ? "" : request.reasonCode()))
            || (request.result() == StudyResult.COMPLETED && request.reasonCode() != null))
            throw new CustomException(TimerErrorCode.INVALID_RESULT);
        var context = scheduleQuery.findOwnedItemForUpdate(memberId, scheduleId, itemId);
        var previous = repository.findByAttemptId(request.attemptId());
        if (previous.isPresent()) {
            if (!previous.get().matches(memberId, scheduleId, itemId, request))
                throw new CustomException(TimerErrorCode.ATTEMPT_CONFLICT);
            return previous.get();
        }
        // One row per attempt: repeated failures remain separate records; scheduling state belongs to Schedule.
        return repository.saveAndFlush(TimerResult.create(memberId, scheduleId, itemId, context, request));
    }

    @Transactional(readOnly = true)
    public List<TimerResultResponse> findHistory(Long memberId, Long scheduleId, Long itemId) {
        scheduleQuery.findOwnedItem(memberId, scheduleId, itemId);
        return repository.findAllByMemberIdAndScheduleIdAndScheduleItemIdOrderByCreatedAtDesc(
                memberId, scheduleId, itemId).stream()
            .map(TimerResultResponse::from)
            .toList();
    }
}
