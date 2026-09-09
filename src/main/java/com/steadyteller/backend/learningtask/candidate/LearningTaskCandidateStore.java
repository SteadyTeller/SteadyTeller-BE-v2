package com.steadyteller.backend.learningtask.candidate;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.exception.LearningTaskErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 검토(승인) 전 후보 태스크를 보관하는 서버 메모리 캐시.
 * <p>
 * 승인 전까지 DB에 저장하지 않는다는 설계 원칙에 따라, 후보 목록은 이 인메모리 저장소에서만 관리하고
 * confirm 호출 시점에만 LearningTask로 일괄 저장한다.
 * <p>
 * 주의: 단일 인스턴스 기준 데모 수준 구현이다. 서버 재시작이나 다중 인스턴스 환경에서는 후보 데이터가
 * 유실/불일치할 수 있으며, 필요 시 Redis 등 외부 캐시로 교체할 수 있다.
 */
@Component
public class LearningTaskCandidateStore {

    private final Map<Long, LearningTaskCandidate> candidates = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public Long nextId() {
        return idGenerator.getAndIncrement();
    }

    public void add(LearningTaskCandidate candidate) {
        candidates.put(candidate.getCandidateId(), candidate);
    }

    public void replaceForGoal(Long goalId, List<LearningTaskCandidate> newCandidates) {
        clearForGoal(goalId);
        newCandidates.forEach(this::add);
    }

    public List<LearningTaskCandidate> findByGoal(Long goalId) {
        return candidates.values().stream()
                .filter(candidate -> candidate.getGoalId().equals(goalId))
                .sorted(Comparator.comparing(LearningTaskCandidate::getCandidateId))
                .toList();
    }

    public LearningTaskCandidate get(Long candidateId) {
        LearningTaskCandidate candidate = candidates.get(candidateId);
        if (candidate == null) {
            throw new CustomException(LearningTaskErrorCode.CANDIDATE_NOT_FOUND);
        }
        return candidate;
    }

    public void remove(Long candidateId) {
        candidates.remove(candidateId);
    }

    public void clearForGoal(Long goalId) {
        candidates.values().removeIf(candidate -> candidate.getGoalId().equals(goalId));
    }
}
