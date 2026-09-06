package com.steadyteller.backend.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.member.domain.Availability;
import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.dto.AvailabilityRequest;
import com.steadyteller.backend.member.dto.AvailabilityResponse;
import com.steadyteller.backend.member.exception.MemberErrorCode;
import com.steadyteller.backend.member.repository.AvailabilityRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private MemberService memberService;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @InjectMocks
    private AvailabilityService availabilityService;

    @Test
    void createAvailabilityUsesEnabledDefaultAndCalculatesMinutes() {
        Long memberId = 1L;
        Member member = Member.create("member@example.com", "encoded", "steady");
        AvailabilityRequest request = AvailabilityRequest.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 0))
                .build();
        given(memberService.getActiveMember(memberId)).willReturn(member);
        given(availabilityRepository.findAllByMemberIdAndDayOfWeek(memberId, DayOfWeek.MONDAY))
                .willReturn(List.of());
        given(availabilityRepository.save(any(Availability.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        AvailabilityResponse response = availabilityService.createAvailability(memberId, request);

        assertThat(response.getAvailableMinutes()).isEqualTo(120);
        assertThat(response.isEnabled()).isTrue();
    }

    @Test
    void createAvailabilityRejectsOverlappingTime() {
        Long memberId = 1L;
        Member member = Member.create("member@example.com", "encoded", "steady");
        AvailabilityRequest request = AvailabilityRequest.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(20, 0))
                .endTime(LocalTime.of(22, 0))
                .build();
        Availability existing = Availability.builder()
                .id(10L)
                .member(member)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(19, 0))
                .endTime(LocalTime.of(21, 0))
                .availableMinutes(120)
                .enabled(true)
                .build();
        given(memberService.getActiveMember(memberId)).willReturn(member);
        given(availabilityRepository.findAllByMemberIdAndDayOfWeek(memberId, DayOfWeek.MONDAY))
                .willReturn(List.of(existing));

        assertThatThrownBy(() -> availabilityService.createAvailability(memberId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.AVAILABILITY_OVERLAP);
    }

    @Test
    void createAvailabilityRejectsInvalidTimeRange() {
        AvailabilityRequest request = AvailabilityRequest.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(21, 0))
                .endTime(LocalTime.of(20, 0))
                .build();

        assertThatThrownBy(() -> availabilityService.createAvailability(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.INVALID_TIME_RANGE);
    }
}
