package com.steadyteller.backend.learningtask.exception;
import com.steadyteller.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
@Getter @AllArgsConstructor
public enum LearningTaskErrorCode implements ErrorCode {
 CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND,"T001","후보 태스크를 찾을 수 없습니다."),
 AI_GENERATION_FAILED(HttpStatus.BAD_GATEWAY,"T003","AI 태스크 생성에 실패했습니다."),
 PLAN_EXCEEDS_AVAILABLE_TIME(HttpStatus.CONFLICT,"T004","가용시간에 맞는 후보 태스크를 생성할 수 없습니다."),
 CONFIRMED_TASK_NOT_FOUND(HttpStatus.NOT_FOUND,"T005","확정 태스크를 찾을 수 없습니다."),
 COMPLETED_TASK_CANNOT_BE_DELETED(HttpStatus.CONFLICT,"T006","완료된 태스크는 삭제할 수 없습니다."),
 TASK_GENERATION_LOCKED(HttpStatus.CONFLICT,"T007","태스크가 이미 확정되었습니다. 재계획을 이용하세요."),
 PLAN_CANT_GENERATE_WITHIN_AVAILABLE_TIME(HttpStatus.BAD_REQUEST, "T008", "목표일까지 사용 가능한 가용시간이 없습니다. 목표일을 변경하거나 가용시간을 추가해주세요.");
 private final HttpStatus status; private final String code; private final String message;
}
