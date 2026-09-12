package com.steadyteller.backend.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.learningtask.entity.LearningTask;
import com.steadyteller.backend.learningtask.entity.LearningTaskSource;
import com.steadyteller.backend.learningtask.entity.LearningTaskStatus;
import com.steadyteller.backend.learningtask.repository.LearningTaskRepository;
import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.repository.MemberRepository;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.entity.ScheduleItemStatus;
import com.steadyteller.backend.schedule.exception.ScheduleErrorCode;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("dev")
class ScheduleServiceConcurrencyTest {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberGoalRepository memberGoalRepository;

    @Autowired
    private LearningTaskRepository learningTaskRepository;

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
        learningTaskRepository.deleteAll();
        memberGoalRepository.deleteAll();
        memberRepository.deleteAll();

        Member member = memberRepository.save(Member.create("concurrency@example.com", "pass1234!", "동시성테스터"));
        memberId = member.getId();

        MemberGoal goal = memberGoalRepository.save(MemberGoal.builder()
                .memberId(memberId)
                .title("동시성 테스트 목표")
                .startDate(LocalDate.of(2026, 8, 24))
                .targetDate(LocalDate.of(2026, 11, 1))
                .currentLevel("초급")
                .dailyStudyHours(1)
                .availableDays(List.of("MON", "WED"))
                .focusArea("동시성")
                .build());
        goalId = goal.getId();

        LearningTask task1 = LearningTask.builder()
                .goalId(goalId)
                .title("태스크 1")
                .category("카테고리")
                .subject("주제")
                .importance(2)
                .difficulty(2)
                .allocatedMinutes(30)
                .source(LearningTaskSource.AI_GENERATED)
                .isModified(false)
                .build();

        LearningTask task2 = LearningTask.builder()
                .goalId(goalId)
                .title("태스크 2")
                .category("카테고리")
                .subject("주제")
                .importance(3)
                .difficulty(3)
                .allocatedMinutes(30)
                .source(LearningTaskSource.AI_GENERATED)
                .isModified(false)
                .build();

        learningTaskRepository.saveAll(List.of(task1, task2));
    }

    @Test
    @DisplayName("동일 goalId에 대해 동시에 2개의 스케줄 생성 요청이 들어오면 1개만 성공하고 1개는 NO_CONFIRMED_TASKS로 방어된다")
    void concurrentScheduleGenerationOnlyCreatesOneSchedule() throws InterruptedException {
        // Given: AI가 스케줄을 정상 반환하도록 모킹하되, 동시성 경합 유도를 위해 약간의 딜레이 부여
        given(scheduleAiService.generateSchedule(any(), any(), any(), any(), anyInt(), anyInt()))
                .willAnswer(invocation -> {
                    List<LearningTask> tasks = invocation.getArgument(1);
                    LocalDate start = invocation.getArgument(2);
                    Thread.sleep(50); // 동시성 경합 시뮬레이션
                    return List.of(
                            new ScheduleAllocator.AllocatedItem(tasks.get(0), start, tasks.get(0).getAllocatedMinutes(), 1),
                            new ScheduleAllocator.AllocatedItem(tasks.get(1), start, tasks.get(1).getAllocatedMinutes(), 2)
                    );
                });

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When
        Future<?> future1 = executorService.submit(() -> {
            try {
                startLatch.await();
                scheduleService.generateSchedule(memberId, goalId);
                successCount.incrementAndGet();
            } catch (CustomException e) {
                if (e.getErrorCode() == ScheduleErrorCode.NO_CONFIRMED_TASKS) {
                    failCount.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
        });

        Future<?> future2 = executorService.submit(() -> {
            try {
                startLatch.await();
                scheduleService.generateSchedule(memberId, goalId);
                successCount.incrementAndGet();
            } catch (CustomException e) {
                if (e.getErrorCode() == ScheduleErrorCode.NO_CONFIRMED_TASKS) {
                    failCount.incrementAndGet();
                }
            } catch (Exception ignored) {
            }
        });

        startLatch.countDown(); // 동시 시작

        try {
            future1.get();
            future2.get();
        } catch (ExecutionException e) {
            // Unhandled exception
        }

        executorService.shutdown();

        // Then
        // 1) 둘 중 정확히 1개 요청만 성공하고, 1개는 NO_CONFIRMED_TASKS 예외 발생
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        // 2) 생성된 스케줄은 DB에 정확히 1개만 존재해야 함
        List<Schedule> schedules = scheduleRepository.findByGoalIdOrderByStartDateDesc(goalId);
        assertThat(schedules).hasSize(1);

        // 3) 모든 학습 태스크의 상태가 SCHEDULED 로 정상 전이되었어야 함
        List<LearningTask> updatedTasks = learningTaskRepository.findAll();
        assertThat(updatedTasks).allMatch(t -> t.getStatus() == LearningTaskStatus.SCHEDULED);
    }

    @Test
    @DisplayName("start와 complete 요청이 동시에 경합해도 비관적 락에 의해 최종 상태는 항상 FINISHED로 보장된다")
    void concurrentStartAndCompleteGuaranteesFinishedStatus() throws InterruptedException, ExecutionException {
        // Given: 스케줄 및 PENDING 상태의 스케줄 항목 1개 생성
        Schedule schedule = scheduleRepository.save(
                Schedule.create(memberId, goalId, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)));
        ScheduleItem item = scheduleItemRepository.save(
                ScheduleItem.create(schedule, 100L, "동시성 테스트 항목", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 30, 1));

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        // When: 쓰레드 1은 complete 호출, 쓰레드 2는 start 호출 (동시 시작)
        Future<?> completeFuture = executorService.submit(() -> {
            try {
                startLatch.await();
                scheduleService.completeScheduleItem(memberId, schedule.getId(), item.getId());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> startFuture = executorService.submit(() -> {
            try {
                startLatch.await();
                scheduleService.startScheduleItem(memberId, schedule.getId(), item.getId());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        startLatch.countDown(); // 동시 릴리즈

        completeFuture.get();
        startFuture.get();
        executorService.shutdown();

        // Then: 어떤 순서로 락을 획득하든 최종 DB 상태는 반드시 FINISHED 여야 함
        ScheduleItem updatedItem = scheduleItemRepository.findById(item.getId()).orElseThrow();
        assertThat(updatedItem.getStatus()).isEqualTo(ScheduleItemStatus.FINISHED);
    }
}
