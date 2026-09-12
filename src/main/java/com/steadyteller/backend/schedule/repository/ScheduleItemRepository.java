package com.steadyteller.backend.schedule.repository;

import com.steadyteller.backend.schedule.entity.ScheduleItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    List<ScheduleItem> findByScheduleIdOrderByDateAscOrderIndexAsc(Long scheduleId);

    // 완료 처리/취소는 순서 재배치가 필요 없어 updateScheduleItem처럼 전체 목록을 불러올 필요가 없으므로,
    // 스케줄 소속 여부까지 함께 검증하는 단건 조회로 처리한다.
    Optional<ScheduleItem> findByIdAndScheduleId(Long id, Long scheduleId);

    long countByScheduleId(Long scheduleId);

    // 파생 delete 메서드(deleteBy...)는 내부적으로 건별 삭제를 수행하므로,
    // 스케줄 삭제 시 항목이 많아도 한 번의 DELETE 문으로 끝나도록 벌크 쿼리로 직접 작성한다.
    @Modifying
    @Query("DELETE FROM ScheduleItem si WHERE si.schedule.id = :scheduleId")
    void deleteByScheduleId(@Param("scheduleId") Long scheduleId);
}
