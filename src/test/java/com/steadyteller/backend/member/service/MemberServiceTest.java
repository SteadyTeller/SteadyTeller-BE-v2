package com.steadyteller.backend.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.dto.MemberResponse;
import com.steadyteller.backend.member.dto.MemberSignUpRequest;
import com.steadyteller.backend.member.exception.MemberErrorCode;
import com.steadyteller.backend.member.repository.LearningProfileRepository;
import com.steadyteller.backend.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private LearningProfileRepository learningProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    @Test
    void signUpCreatesMemberAndDefaultProfile() {
        MemberSignUpRequest request = MemberSignUpRequest.builder()
                .email("member@example.com")
                .password("password123")
                .nickname("steady")
                .build();
        Member savedMember = Member.create("member@example.com", "encoded", "steady");

        given(memberRepository.existsByEmail(request.getEmail())).willReturn(false);
        given(passwordEncoder.encode(request.getPassword())).willReturn("encoded");
        given(memberRepository.save(any(Member.class))).willReturn(savedMember);

        MemberResponse response = memberService.signUp(request);

        assertThat(response.getEmail()).isEqualTo("member@example.com");
        assertThat(response.getNickname()).isEqualTo("steady");
        verify(learningProfileRepository).save(any());
    }

    @Test
    void signUpRejectsDuplicateEmail() {
        MemberSignUpRequest request = MemberSignUpRequest.builder()
                .email("member@example.com")
                .password("password123")
                .nickname("steady")
                .build();
        given(memberRepository.existsByEmail(request.getEmail())).willReturn(true);

        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);
    }
}
