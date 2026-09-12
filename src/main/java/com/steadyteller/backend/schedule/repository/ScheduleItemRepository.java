package com.steadyteller.backend.schedule.repository;

import com.steadyteller.backend.schedule.entity.ScheduleItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    List<ScheduleItem> findByScheduleIdOrderByDateAscOrderIndexAsc(Long scheduleId);

    // 완료 처리/취소는 순서 재배치가 필요 없어 updateScheduleItem처럼 전체 목록을 불러올 필요가 없으므로,
    // 스케줄 소속 여부까지 함께 검증하는 단건 조회로 처리한다.
    Optional<ScheduleItem> findByIdAndScheduleId(Long id, Long scheduleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT si FROM ScheduleItem si WHERE si.id = :id AND si.schedule.id = :scheduleId")
    Optional<ScheduleItem> findByIdAndScheduleIdForUpdate(@Param("id") Long id, @Param("scheduleId") Long scheduleId);

    long countByScheduleId(Long scheduleId);
}
