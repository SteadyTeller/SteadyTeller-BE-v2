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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityService {

    private final MemberService memberService;
    private final AvailabilityRepository availabilityRepository;

    public List<AvailabilityResponse> getAvailabilities(Long memberId) {
        memberService.getActiveMember(memberId);
        return availabilityRepository.findAllByMemberIdOrderByDayOfWeekAscStartTimeAsc(memberId)
                .stream()
                .map(AvailabilityResponse::from)
                .toList();
    }

    @Transactional
    public AvailabilityResponse createAvailability(Long memberId, AvailabilityRequest request) {
        validateTimeRange(request);
        Member member = memberService.getActiveMember(memberId);
        validateOverlap(memberId, null, request);
        Availability availability = Availability.create(
                member,
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
            Long availabilityId,
            AvailabilityRequest request
    ) {
        validateTimeRange(request);
        memberService.getActiveMember(memberId);
        validateOverlap(memberId, availabilityId, request);
        Availability availability = getAvailability(memberId, availabilityId);
        availability.update(
                request.getDayOfWeek(),
                request.getStartTime(),
                request.getEndTime(),
                request.isEnabledOrDefault()
        );
        return AvailabilityResponse.from(availability);
    }

    @Transactional
    public void deleteAvailability(Long memberId, Long availabilityId) {
        memberService.getActiveMember(memberId);
        availabilityRepository.delete(getAvailability(memberId, availabilityId));
    }

    private Availability getAvailability(Long memberId, Long availabilityId) {
        return availabilityRepository.findByIdAndMemberId(availabilityId, memberId)
                .orElseThrow(() -> new CustomException(MemberErrorCode.AVAILABILITY_NOT_FOUND));
    }

    private void validateTimeRange(AvailabilityRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new CustomException(MemberErrorCode.INVALID_TIME_RANGE);
        }
    }

    private void validateOverlap(
            Long memberId,
            Long excludedAvailabilityId,
            AvailabilityRequest request
    ) {
        boolean overlaps = availabilityRepository
                .findAllByMemberIdAndDayOfWeek(memberId, request.getDayOfWeek())
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
