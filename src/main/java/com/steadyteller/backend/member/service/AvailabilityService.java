package com.steadyteller.backend.member.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.member.domain.Availability;
import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.dto.AvailabilityReplaceRequest;
import com.steadyteller.backend.member.dto.AvailabilityRequest;
import com.steadyteller.backend.member.dto.AvailabilityResponse;
import com.steadyteller.backend.member.exception.MemberErrorCode;
import com.steadyteller.backend.member.repository.AvailabilityRepository;
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
public class AvailabilityService {
    private final MemberService memberService;
    private final AvailabilityRepository availabilityRepository;
    private final MemberGoalRepository memberGoalRepository;

    public List<AvailabilityResponse> getAvailabilities(Long memberId, Long goalId) {
        memberService.getActiveMember(memberId);
        verifyGoalOwnership(memberId, goalId);
        return availabilityRepository.findAllByMemberGoalIdOrderByDayOfWeekAscStartTimeAsc(goalId).stream()
                .map(AvailabilityResponse::from).toList();
    }

    @Transactional
    public List<AvailabilityResponse> replaceAvailabilities(Long memberId, Long goalId,
                                                             AvailabilityReplaceRequest request) {
        Member member = memberService.getActiveMember(memberId);
        getEditableGoal(memberId, goalId);
        List<AvailabilityRequest> windows = request.availabilities();
        validate(windows);
        availabilityRepository.deleteByMemberGoalId(goalId);
        return availabilityRepository.saveAll(windows.stream().map(item -> Availability.create(member, goalId,
                item.getDayOfWeek(), item.getStartTime(), item.getEndTime(), item.isEnabledOrDefault())).toList())
                .stream().map(AvailabilityResponse::from).toList();
    }

    private void validate(List<AvailabilityRequest> windows) {
        for (int i = 0; i < windows.size(); i++) {
            AvailabilityRequest left = windows.get(i);
            if (!left.getEndTime().isAfter(left.getStartTime())) {
                throw new CustomException(MemberErrorCode.INVALID_TIME_RANGE);
            }
            for (int j = i + 1; j < windows.size(); j++) {
                AvailabilityRequest right = windows.get(j);
                if (left.getDayOfWeek() == right.getDayOfWeek()
                        && left.getStartTime().isBefore(right.getEndTime())
                        && left.getEndTime().isAfter(right.getStartTime())) {
                    throw new CustomException(MemberErrorCode.AVAILABILITY_OVERLAP);
                }
            }
        }
    }

    private void verifyGoalOwnership(Long memberId, Long goalId) {
        MemberGoal goal = memberGoalRepository.findById(goalId)
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
    }

    private MemberGoal getEditableGoal(Long memberId, Long goalId) {
        MemberGoal goal = memberGoalRepository.findById(goalId)
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
        if (goal.isTaskGenerationLocked()) throw new CustomException(GoalErrorCode.GOAL_ALREADY_PLANNED);
        return goal;
    }
}
