package com.steadyteller.backend.learningtask.dto;

/**
 * AI(Spring AI ChatClient)가 생성하는 구조화된 태스크 결과를 그대로 매핑하는 DTO.
 * orderIndex는 이 단계에서 결정하지 않는다 (스케줄 생성 단계 소관).
 */
public record AiGeneratedTaskDto(
        String title,
        String category,
        String subject,
        Integer difficulty,
        Integer allocatedMinutes
) {
}
