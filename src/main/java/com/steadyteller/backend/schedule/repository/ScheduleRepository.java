package com.steadyteller.backend.schedule.repository;

import com.steadyteller.backend.schedule.entity.Schedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByGoalIdOrderByStartDateDesc(Long goalId);
}
