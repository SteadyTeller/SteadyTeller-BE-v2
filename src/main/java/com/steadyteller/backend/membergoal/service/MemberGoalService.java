package com.steadyteller.backend.membergoal.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.membergoal.dto.MemberGoalResponseDto;
import com.steadyteller.backend.membergoal.dto.MemberStudyInfoRequestDto;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.exception.GoalErrorCode;
import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberGoalService {

    private final MemberGoalRepository memberGoalRepository;

    @Transactional
    public MemberGoalResponseDto createGoal(Long memberId, MemberStudyInfoRequestDto request) {
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

    @Transactional
    public void deleteGoal(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoal(memberId, goalId);
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
