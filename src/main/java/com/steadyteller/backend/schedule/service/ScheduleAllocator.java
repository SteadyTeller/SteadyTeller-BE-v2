package com.steadyteller.backend.schedule.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.schedule.exception.ScheduleErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 확정된 순서(orderedTasks)를 기준으로 availableDays/dailyCapacityMinutes 제약을 지키며
 * 각 태스크를 실제 날짜에 결정론적으로 배정한다.
 *
 * AI는 태스크 "순서"만 결정하고, 날짜/시간 배정은 이 알고리즘이 담당한다.
 * LLM은 숫자 제약(가용 요일, 일일 한도)을 매번 정확히 지키리라 보장할 수 없으므로,
 * 정확성이 중요한 배정 계산은 서버 코드로 고정하고 AI 산출물은 순서 힌트로만 사용한다.
 */
@Component
public class ScheduleAllocator {

    public record AllocatedItem(LearningTask task, LocalDate date, int allocatedMinutes, int orderInDay) {
    }

    public List<AllocatedItem> allocate(
            List<LearningTask> orderedTasks,
            LocalDate earliestStart,
            Set<DayOfWeek> availableDays,
            int dailyCapacityMinutes,
            int maxHorizonDays
    ) {
        List<AllocatedItem> result = new ArrayList<>();
        Iterator<LearningTask> iterator = orderedTasks.iterator();
        LearningTask pending = iterator.hasNext() ? iterator.next() : null;

        LocalDate cursor = earliestStart;
        int daysWalked = 0;
        while (pending != null) {
            if (daysWalked > maxHorizonDays) {
                throw new CustomException(ScheduleErrorCode.SCHEDULE_GENERATION_FAILED);
            }
            if (!availableDays.contains(cursor.getDayOfWeek())) {
                cursor = cursor.plusDays(1);
                daysWalked++;
                continue;
            }

            int remainingMinutes = dailyCapacityMinutes;
            int orderInDay = 1;
            boolean placedAnyToday = false;
            while (pending != null) {
                int minutes = pending.getAllocatedMinutes();
                // 하루 첫 태스크는 남은 시간을 초과하더라도 반드시 배정한다.
                // 그렇지 않으면 dailyCapacityMinutes보다 긴 단일 태스크가 영원히 배정되지 못한다.
                if (!placedAnyToday || minutes <= remainingMinutes) {
                    result.add(new AllocatedItem(pending, cursor, minutes, orderInDay));
                    orderInDay++;
                    remainingMinutes -= minutes;
                    placedAnyToday = true;
                    pending = iterator.hasNext() ? iterator.next() : null;
                } else {
                    break;
                }
            }
            cursor = cursor.plusDays(1);
            daysWalked++;
        }
        return result;
    }
}
