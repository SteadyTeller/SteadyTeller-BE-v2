package com.steadyteller.backend.learningtask.candidate;

import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import lombok.Getter;

/**
 * 검토(승인) 전 단계의 후보 태스크. DB 엔티티가 아니라 LearningTaskCandidateStore(메모리 캐시)에서만 관리된다.
 * confirm 호출 시 이 정보를 바탕으로 LearningTask 엔티티가 생성되어 저장된다.
 */
@Getter
public class LearningTaskCandidate {

    private final Long candidateId;
    private final Long goalId;
    private final Long memberId;
    private final LearningTaskSource source;

    private String title;
    private String category;
    private String subject;
    private Integer difficulty;
    private Integer allocatedMinutes;
    private boolean modified;

    public LearningTaskCandidate(Long candidateId, Long goalId, Long memberId, String title, String category,
                                  String subject, Integer difficulty, Integer allocatedMinutes,
                                  LearningTaskSource source, boolean modified) {
        this.candidateId = candidateId;
        this.goalId = goalId;
        this.memberId = memberId;
        this.title = title;
        this.category = category;
        this.subject = subject;
        this.difficulty = difficulty;
        this.allocatedMinutes = allocatedMinutes;
        this.source = source;
        this.modified = modified;
    }

    // importance는 프론트 표시 전용 값으로, difficulty와 동일한 값을 사용한다.
    public Integer getImportance() {
        return difficulty;
    }

    public void update(String title, String category, String subject, Integer difficulty, Integer allocatedMinutes) {
        this.title = title;
        this.category = category;
        this.subject = subject;
        this.difficulty = difficulty;
        this.allocatedMinutes = allocatedMinutes;
        this.modified = true;
    }
}
