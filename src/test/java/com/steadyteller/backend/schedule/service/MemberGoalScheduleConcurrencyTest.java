package com.steadyteller.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import com.steadyteller.backend.learningtask.candidate.LearningTaskCandidate;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.learningtask.repository.LearningTaskCandidateRepository;
import com.steadyteller.backend.learningtask.repository.LearningTaskRepository;
import com.steadyteller.backend.learningtask.service.LearningTaskService;
import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.repository.MemberRepository;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.membergoal.service.MemberGoalService;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("dev")
class MemberGoalScheduleConcurrencyTest {

    @Autowired
    private MemberGoalService memberGoalService;

    @Autowired
    private LearningTaskService learningTaskService;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberGoalRepository memberGoalRepository;

    @Autowired
    private LearningTaskRepository learningTaskRepository;

    @Autowired
    private LearningTaskCandidateRepository candidateRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleItemRepository scheduleItemRepository;

    @MockitoBean
    private ScheduleAiService scheduleAiService;

    private Long memberId;
    private Long goalId;

    @BeforeEach
    void setUp() {
        scheduleItemRepository.deleteAll();
        scheduleRepository.deleteAll();
        candidateRepository.deleteAll();
        learningTaskRepository.deleteAll();
        memberGoalRepository.deleteAll();
        memberRepository.deleteAll();

        Member member = memberRepository.save(Member.create("goal_lock@example.com", "pass1234!", "락테스터"));
        memberId = member.getId();

        MemberGoal goal = memberGoalRepository.save(MemberGoal.builder()
                .memberId(memberId)
                .title("동시성 정합성 목표")
                .startDate(LocalDate.of(2026, 8, 24))
                .targetDate(LocalDate.of(2026, 11, 1))
                .currentLevel("초급")
                .dailyStudyHours(1)
                .availableDays(List.of("MON", "WED"))
                .focusArea("동시성 제어")
                .build());
        goalId = goal.getId();
    }

    @Test
    @DisplayName("스케줄 생성과 목표 삭제가 동시에 실행될 때 MemberGoal 락으로 직렬화되어 고아 스케줄이 남지 않는다")
    void concurrentScheduleGenerationAndGoalDeletionLeavesNoOrphanSchedules() throws Exception {
        // Given: PENDING 태스크 2개 준비
        LearningTask task1 = LearningTask.builder()
                .goalId(goalId).title("태스크 1").category("카테고리").subject("주제")
                .importance(2).difficulty(2).allocatedMinutes(30)
                .source(LearningTaskSource.AI_GENERATED).isModified(false).build();
        LearningTask task2 = LearningTask.builder()
                .goalId(goalId).title("태스크 2").category("카테고리").subject("주제")
                .importance(3).difficulty(3).allocatedMinutes(30)
                .source(LearningTaskSource.AI_GENERATED).isModified(false).build();
        learningTaskRepository.saveAll(List.of(task1, task2));

        // 스케줄 AI 응답 모킹 (100ms 지연으로 경합 유도)
        given(scheduleAiService.generateSchedule(any(), any(), any(), any(), anyInt(), anyInt()))
                .willAnswer(invocation -> {
                    List<LearningTask> tasks = invocation.getArgument(1);
                    LocalDate start = invocation.getArgument(2);
                    Thread.sleep(100);
                    return List.of(
                            new ScheduleAllocator.AllocatedItem(tasks.get(0), start, tasks.get(0).getAllocatedMinutes(), 1),
                            new ScheduleAllocator.AllocatedItem(tasks.get(1), start, tasks.get(1).getAllocatedMinutes(), 2)
                    );
                });

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicBoolean scheduleGenerated = new AtomicBoolean(false);
        AtomicBoolean goalDeleted = new AtomicBoolean(false);

        // When: 스케줄 생성과 목표 삭제를 동시 발송
        Future<?> scheduleFuture = executorService.submit(() -> {
            try {
                startLatch.await();
                scheduleService.generateSchedule(memberId, goalId);
                scheduleGenerated.set(true);
            } catch (Exception ignored) {
            }
        });

        Future<?> deleteFuture = executorService.submit(() -> {
            try {
                startLatch.await();
                memberGoalService.deleteGoal(memberId, goalId);
                goalDeleted.set(true);
            } catch (Exception ignored) {
            }
        });

        startLatch.countDown();

        scheduleFuture.get(5, TimeUnit.SECONDS);
        deleteFuture.get(5, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then
        // 1. 목표 삭제는 반드시 완료되어야 함
        assertThat(memberGoalRepository.findById(goalId)).isEmpty();

        // 2. 핵심 불변식: 목표가 삭제된 상태에서 DB에 고아 Schedule이나 ScheduleItem이 절대로 존재해서는 안 됨
        List<Schedule> remainingSchedules = scheduleRepository.findByGoalIdOrderByStartDateDesc(goalId);
        List<ScheduleItem> allItems = scheduleItemRepository.findAll();
        List<LearningTask> allTasks = learningTaskRepository.findAll();

        assertThat(remainingSchedules).isEmpty();
        assertThat(allItems).isEmpty();
        assertThat(allTasks).isEmpty();
    }

    @Test
    @DisplayName("태스크 확정(confirmTasks)과 스케줄 생성(generateSchedule)이 동시 실행될 때 MemberGoal 락으로 직렬화된다")
    void concurrentConfirmTasksAndScheduleGenerationSerializeCorrectly() throws Exception {
        // Given: 후보 2개 저장
        LearningTaskCandidate c1 = LearningTaskCandidate.builder()
                .goalId(goalId).memberId(memberId).title("후보 1").category("카테고리").subject("주제")
                .difficulty(2).allocatedMinutes(30).source(LearningTaskSource.AI_GENERATED).modified(false).build();
        LearningTaskCandidate c2 = LearningTaskCandidate.builder()
                .goalId(goalId).memberId(memberId).title("후보 2").category("카테고리").subject("주제")
                .difficulty(3).allocatedMinutes(30).source(LearningTaskSource.AI_GENERATED).modified(false).build();
        candidateRepository.saveAll(List.of(c1, c2));

        // 기존 PENDING 태스크 1개
        LearningTask oldTask = LearningTask.builder()
                .goalId(goalId).title("구 태스크").category("카테고리").subject("주제")
                .importance(1).difficulty(1).allocatedMinutes(30)
                .source(LearningTaskSource.AI_GENERATED).isModified(false).build();
        learningTaskRepository.save(oldTask);

        given(scheduleAiService.generateSchedule(any(), any(), any(), any(), anyInt(), anyInt()))
                .willAnswer(invocation -> {
                    List<LearningTask> tasks = invocation.getArgument(1);
                    LocalDate start = invocation.getArgument(2);
                    Thread.sleep(80);
                    return tasks.stream()
                            .map(t -> new ScheduleAllocator.AllocatedItem(t, start, t.getAllocatedMinutes(), 1))
                            .toList();
                });

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        // When
        Future<?> confirmFuture = executorService.submit(() -> {
            try {
                startLatch.await();
                learningTaskService.confirmTasks(memberId, goalId);
            } catch (Exception ignored) {
            }
        });

        Future<?> scheduleFuture = executorService.submit(() -> {
            try {
                startLatch.await();
                scheduleService.generateSchedule(memberId, goalId);
            } catch (Exception ignored) {
            }
        });

        startLatch.countDown();

        confirmFuture.get(5, TimeUnit.SECONDS);
        scheduleFuture.get(5, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then: 스케줄이 생성되었다면 생성된 스케줄 항목이 가리키는 LearningTask가 DB에 실제로 온전히 존재해야 함
        List<Schedule> schedules = scheduleRepository.findByGoalIdOrderByStartDateDesc(goalId);
        if (!schedules.isEmpty()) {
            List<ScheduleItem> items = scheduleItemRepository.findByScheduleIdOrderByDateAscOrderIndexAsc(schedules.get(0).getId());
            for (ScheduleItem item : items) {
                assertThat(learningTaskRepository.findById(item.getLearningTaskId())).isPresent();
            }
        }
    }
}
