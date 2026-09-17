package com.steadyteller.backend.membergoal.port;

import com.steadyteller.backend.membergoal.dto.GoalDeadlineResponse.RemainingTask;
import java.util.List;

public interface GoalRemainingTaskQueryPort {
    List<RemainingTask> findRemainingTasks(Long goalId);
}
