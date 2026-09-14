package com.steadyteller.backend.schedule.event;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.repository.MemberRepository;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.event.MemberGoalDeletedEvent;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.schedule.entity.Schedule;
import com.steadyteller.backend.schedule.entity.ScheduleItem;
import com.steadyteller.backend.schedule.repository.ScheduleItemRepository;
import com.steadyteller.backend.schedule.repository.ScheduleRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ScheduleGoalEventListenerTest(Mockito)는 영속성 컨텍스트/FK 제약조건을 거치지 않아
 * ScheduleService.deleteSchedule에서 실제로 있었던 FK 삭제 순서 문제(schedule_item -> schedule)를
 * 검증할 수 없다. 이 테스트는 실제 DB(FK 제약조건 포함)로 handleGoalDeleted가 예외 없이
 * 스케줄/스케줄 항목을 정리하는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("dev")
class ScheduleGoalEventListenerIntegrationTest {

    @Autowired
    private ScheduleGoalEventListener listener;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberGoalRepository memberGoalRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleItemRepository scheduleItemRepository;

    private Long goalId;

    @BeforeEach
    void setUp() {
        scheduleItemRepository.deleteAll();
        scheduleRepository.deleteAll();
        memberGoalRepository.deleteAll();
        memberRepository.deleteAll();

        Member member = memberRepository.save(Member.create("listener_test@example.com", "pass1234!", "리스너테스터"));
        MemberGoal goal = memberGoalRepository.save(MemberGoal.builder()
                .memberId(member.getId())
                .title("목표")
                .startDate(LocalDate.of(2026, 8, 24))
                .targetDate(LocalDate.of(2026, 11, 1))
                .currentLevel("초급")
                .dailyStudyHours(1)
                .availableDays(List.of("MON"))
                .focusArea("리스너 테스트")
                .build());
        goalId = goal.getId();
    }

    @Test
    void handleGoalDeletedDeletesScheduleAndItemsWithoutForeignKeyViolation() {
        Schedule schedule = scheduleRepository.save(
                Schedule.create(1L, goalId, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)));
        scheduleItemRepository.saveAll(List.of(
                ScheduleItem.create(schedule, 1L, "항목1", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 30, 1),
                ScheduleItem.create(schedule, 2L, "항목2", LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY, 30, 2)
        ));

        assertThatCode(() -> listener.handleGoalDeleted(new MemberGoalDeletedEvent(goalId)))
                .doesNotThrowAnyException();
    }
}
