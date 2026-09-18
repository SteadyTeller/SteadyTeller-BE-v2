package com.steadyteller.backend.member.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.member.domain.Availability;
import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.dto.AvailabilityRequest;
import com.steadyteller.backend.member.dto.AvailabilityResponse;
import com.steadyteller.backend.member.exception.MemberErrorCode;
import com.steadyteller.backend.member.repository.AvailabilityRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.steadyteller.backend.membergoal.repository.MemberGoalRepository;
import com.steadyteller.backend.membergoal.entity.MemberGoal;
import com.steadyteller.backend.membergoal.exception.GoalErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityService {

    private final MemberService memberService;
    private final AvailabilityRepository availabilityRepository;
    private final MemberGoalRepository memberGoalRepository;

    private void verifyGoalOwnership(Long memberId, Long memberGoalId) {
        MemberGoal goal = memberGoalRepository.findById(memberGoalId)
                .orElseThrow(() -> new CustomException(GoalErrorCode.GOAL_NOT_FOUND));
        if (!goal.isOwnedBy(memberId)) {
            throw new CustomException(GoalErrorCode.GOAL_ACCESS_DENIED);
        }
    }

    public List<AvailabilityResponse> getAvailabilities(Long memberId, Long memberGoalId) {
        memberService.getActiveMember(memberId);
        verifyGoalOwnership(memberId, memberGoalId);
        return availabilityRepository.findAllByMemberGoalIdOrderByDayOfWeekAscStartTimeAsc(memberGoalId)
                .stream()
                .map(AvailabilityResponse::from)
                .toList();
    }

    @Transactional
    public AvailabilityResponse createAvailability(Long memberId, Long memberGoalId, AvailabilityRequest request) {
        validateTimeRange(request);
        Member member = memberService.getActiveMember(memberId);
        verifyGoalOwnership(memberId, memberGoalId);
        validateOverlap(memberGoalId, null, request);
        Availability availability = Availability.create(
                member,
                memberGoalId,
                request.getDayOfWeek(),
                request.getStartTime(),
                request.getEndTime(),
                request.isEnabledOrDefault()
        );
        return AvailabilityResponse.from(availabilityRepository.save(availability));
    }

    @Transactional
    public AvailabilityResponse updateAvailability(
            Long memberId,
            Long memberGoalId,
            Long availabilityId,
            AvailabilityRequest request
    ) {
        validateTimeRange(request);
        memberService.getActiveMember(memberId);
        verifyGoalOwnership(memberId, memberGoalId);
        validateOverlap(memberGoalId, availabilityId, request);
        Availability availability = getAvailability(memberGoalId, availabilityId);
        availability.update(
                request.getDayOfWeek(),
                request.getStartTime(),
                request.getEndTime(),
                request.isEnabledOrDefault()
        );
        return AvailabilityResponse.from(availability);
    }

    @Transactional
    public void deleteAvailability(Long memberId, Long memberGoalId, Long availabilityId) {
        memberService.getActiveMember(memberId);
        verifyGoalOwnership(memberId, memberGoalId);
        availabilityRepository.delete(getAvailability(memberGoalId, availabilityId));
    }

    private Availability getAvailability(Long memberGoalId, Long availabilityId) {
        return availabilityRepository.findByIdAndMemberGoalId(availabilityId, memberGoalId)
                .orElseThrow(() -> new CustomException(MemberErrorCode.AVAILABILITY_NOT_FOUND));
    }

    private void validateTimeRange(AvailabilityRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new CustomException(MemberErrorCode.INVALID_TIME_RANGE);
        }
    }

    private void validateOverlap(
            Long memberGoalId,
            Long excludedAvailabilityId,
            AvailabilityRequest request
    ) {
        boolean overlaps = availabilityRepository
                .findAllByMemberGoalIdAndDayOfWeek(memberGoalId, request.getDayOfWeek())
                .stream()
                .filter(availability -> !Objects.equals(availability.getId(), excludedAvailabilityId))
                .anyMatch(availability ->
                        request.getStartTime().isBefore(availability.getEndTime())
                                && request.getEndTime().isAfter(availability.getStartTime())
                );
        if (overlaps) {
            throw new CustomException(MemberErrorCode.AVAILABILITY_OVERLAP);
        }
    }
}
