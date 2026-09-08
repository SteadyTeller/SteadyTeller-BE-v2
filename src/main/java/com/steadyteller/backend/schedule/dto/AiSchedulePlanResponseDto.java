package com.steadyteller.backend.schedule.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * AI(Spring AI ChatClient)가 반환하는 일자별 스케줄 직배정 계획 및 백업용 순서 DTO.
 *
 * - dailyPlans: AI가 과목 병행(Interleaving)과 분산 학습을 고려해 일자별로 직접 배정한 계획 (Tier 1)
 * - fallbackTaskOrder: dailyPlans 검증 실패 시 ScheduleAllocator로 안전 배정하기 위한 백업 순서 (Tier 2)
 */
public record AiSchedulePlanResponseDto(
        List<DailyPlanDto> dailyPlans,
        List<Long> fallbackTaskOrder
) {
    public record DailyPlanDto(
            LocalDate date,
            List<Long> taskIds
    ) {
    }
}
