package com.steadyteller.backend.membergoal.event;

/**
 * 학습 목표가 삭제될 때 발행되는 도메인 이벤트.
 * 연관된 후보 태스크, 확정된 태스크, 스케줄 등 하위 도메인 리소스의 정리를 위해 사용된다.
 */
public record MemberGoalDeletedEvent(Long goalId) {
}
