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
 * =========================================================================================
 * [FUTURE EXTENSION: 방법 ③ 태스크 분할(Task Splitting / Chunking) 전환 가이드]
 * =========================================================================================
 * 향후 긴 태스크(예: 70분)를 일자별로 쪼개어(예: Day1에 60분, Day2에 10분) 배정하는
 * 완전 분할 모델로 전환할 경우 아래와 같이 구현할 수 있습니다.
 *
 * 1. DB/Entity 확장:
 *    - ScheduleItem에 chunkIndex(차수, 1, 2..), isLastChunk(boolean), totalEstimatedMinutes 추가
 *    - 또는 LearningTask에 remainingMinutes 개념 추가
 *
 * 2. 분할 배정 알고리즘 변경:
 *    while (pending != null) {
 *        int minutes = pendingRemainingMinutes;
 *        if (minutes <= remainingMinutes) {
 *            result.add(new AllocatedItem(pending, cursor, minutes, orderInDay++, isLastChunk=true));
 *            remainingMinutes -= minutes;
 *            pending = iterator.hasNext() ? iterator.next() : null;
 *        } else if (remainingMinutes > 0) {
 *            // 당일 잔여 시간만큼만 잘라서 배정하고 나머지는 다음 날로 이월
 *            result.add(new AllocatedItem(pending, cursor, remainingMinutes, orderInDay++, isLastChunk=false));
 *            pendingRemainingMinutes = minutes - remainingMinutes;
 *            break; // 당일 소진 후 익일로 이동
 *        } else {
 *            break;
 *        }
 *    }
 * =========================================================================================
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
