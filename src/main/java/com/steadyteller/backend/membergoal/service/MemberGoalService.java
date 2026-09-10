package com.steadyteller.backend.membergoal.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.global.exception.GlobalErrorCode;
import com.steadyteller.backend.membergoal.dto.MemberGoalResponseDto;
import com.steadyteller.backend.membergoal.dto.MemberStudyInfoRequestDto;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.exception.GoalErrorCode;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import com.steadyteller.backend.membergoal.event.MemberGoalDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberGoalService {

    private static final Set<String> VALID_AVAILABLE_DAYS =
            Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    private final MemberGoalRepository memberGoalRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MemberGoalResponseDto createGoal(Long memberId, MemberStudyInfoRequestDto request) {
        validateGoalRequest(request);
        MemberGoal goal = MemberGoal.builder()
                .memberId(memberId)
                .title(request.title())
                .startDate(request.startDate())
                .targetDate(request.targetDate())
                .currentLevel(request.currentLevel())
                .dailyStudyHours(request.dailyStudyHours())
                .availableDays(request.availableDays())
                .focusArea(request.focusArea())
                .build();

        return MemberGoalResponseDto.from(memberGoalRepository.save(goal));
    }

    public List<MemberGoalResponseDto> getGoals(Long memberId) {
        return memberGoalRepository.findByMemberId(memberId).stream()
                .map(MemberGoalResponseDto::from)
                .toList();
    }

    public MemberGoalResponseDto getGoal(Long memberId, Long goalId) {
        return MemberGoalResponseDto.from(getOwnedGoal(memberId, goalId));
    }

    @Transactional
    public MemberGoalResponseDto updateGoal(Long memberId, Long goalId, MemberStudyInfoRequestDto request) {
        validateGoalRequest(request);
        MemberGoal goal = getOwnedGoal(memberId, goalId);
        goal.update(
                request.title(),
                request.startDate(),
                request.targetDate(),
                request.currentLevel(),
                request.dailyStudyHours(),
                request.availableDays(),
                request.focusArea()
        );
        return MemberGoalResponseDto.from(goal);
    }

    private void validateGoalRequest(MemberStudyInfoRequestDto request) {
        if (request.targetDate().isBefore(LocalDate.now())
                || request.startDate().isAfter(request.targetDate())
                || request.availableDays().stream().anyMatch(day -> !VALID_AVAILABLE_DAYS.contains(day))
                || request.availableDays().size() != request.availableDays().stream().distinct().count()) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Transactional
    public void deleteGoal(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoal(memberId, goalId);
        eventPublisher.publishEvent(new MemberGoalDeletedEvent(goalId));
        memberGoalRepository.delete(goal);
    }

    private MemberGoal getOwnedGoal(Long memberId, Long goalId) {
        MemberGoal goal = memberGoalRepository.findById(goalId)
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) {
            throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
        }
        return goal;
    }
}
