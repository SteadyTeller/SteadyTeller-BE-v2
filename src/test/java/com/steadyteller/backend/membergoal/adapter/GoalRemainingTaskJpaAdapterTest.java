package com.steadyteller.backend.membergoal.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.learningtask.repository.LearningTaskRepository;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class GoalRemainingTaskJpaAdapterTest {
    @Autowired private GoalRemainingTaskJpaAdapter adapter;
    @Autowired private LearningTaskRepository tasks;
    @Autowired private ScheduleRepository schedules;
    @Autowired private ScheduleItemRepository items;

    @Test
    void readsUnscheduledAndPartiallyFinishedTasksButNotFullyFinishedOrOtherGoals() {
        LearningTask unscheduled = task(10L);
        LearningTask finished = task(10L);
        LearningTask partial = task(10L);
        task(20L);
        LocalDate date = LocalDate.now();
        Schedule schedule = schedules.save(Schedule.create(1L, 10L, date, date));
        item(schedule, finished, date, true);
        item(schedule, partial, date, true);
        item(schedule, partial, date, false);
        assertThat(adapter.findRemainingTasks(10L)).extracting(value -> value.taskId())
                .containsExactly(unscheduled.getId(), partial.getId());
    }

    private LearningTask task(Long goalId) {
        return tasks.save(LearningTask.builder().goalId(goalId).title("Java")
                .category("개발").subject("Java").importance(3).difficulty(3)
                .allocatedMinutes(30).source(LearningTaskSource.AI_GENERATED).isModified(false).build());
    }

    private void item(Schedule schedule, LearningTask task, LocalDate date, boolean finished) {
        ScheduleItem item = ScheduleItem.create(schedule, task.getId(), task.getTitle(), date, date.getDayOfWeek(), 30, 1);
        if (finished) item.finish();
        items.save(item);
    }
}
