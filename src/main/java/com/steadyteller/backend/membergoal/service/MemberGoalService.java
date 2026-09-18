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
import lombok.RequiredArgsConstructor;
import com.steadyteller.backend.membergoal.event.MemberGoalDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberGoalService {

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
                .mustStudyTopics(request.mustStudyTopics())
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
        MemberGoal goal = getOwnedGoalForUpdate(memberId, goalId);
        goal.update(
                request.title(),
                request.startDate(),
                request.targetDate(),
                request.currentLevel(),
                request.mustStudyTopics()
        );
        return MemberGoalResponseDto.from(goal);
    }

    private void validateGoalRequest(MemberStudyInfoRequestDto request) {
        if (request.targetDate().isBefore(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")))
                || request.startDate().isAfter(request.targetDate())
                || request.mustStudyTopics() == null
                || request.mustStudyTopics().isEmpty()
                || request.mustStudyTopics().stream().anyMatch(topic -> topic == null || topic.isBlank())
                || request.mustStudyTopics().size() != request.mustStudyTopics().stream().map(String::trim).distinct().count()) {
            throw new CustomException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Transactional
    public void closeGoal(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoalForUpdate(memberId, goalId);
        goal.close();
    }

    @Transactional
    public void deleteGoal(Long memberId, Long goalId) {
        MemberGoal goal = getOwnedGoalForUpdate(memberId, goalId);
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

    private MemberGoal getOwnedGoalForUpdate(Long memberId, Long goalId) {
        MemberGoal goal = memberGoalRepository.findByIdForUpdate(goalId)
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) {
            throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
        }
        return goal;
    }
}
