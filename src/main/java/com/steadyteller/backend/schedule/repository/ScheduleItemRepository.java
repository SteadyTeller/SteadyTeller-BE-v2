package com.steadyteller.backend.schedule.repository;

import com.steadyteller.backend.schedule.entity.ScheduleItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    List<ScheduleItem> findByScheduleIdOrderByDateAscOrderIndexAsc(Long scheduleId);

    long countByScheduleId(Long scheduleId);
}
