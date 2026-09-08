package com.steadyteller.backend.schedule.dto;

import java.util.List;

/**
 * AI(Spring AI ChatClient)가 결정한 학습 태스크 수행 순서를 그대로 매핑하는 DTO.
 * 실제 날짜/시간 배정은 이 순서를 힌트로 ScheduleAllocator가 결정론적으로 계산한다.
 */
public record AiTaskOrderResponseDto(
        List<Long> orderedLearningTaskIds
) {
}
