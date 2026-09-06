package com.steadyteller.backend.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.global.security.JwtTokenProvider;
import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.domain.MemberStatus;
import com.steadyteller.backend.member.dto.LoginRequest;
import com.steadyteller.backend.member.dto.LoginResponse;
import com.steadyteller.backend.member.exception.MemberErrorCode;
import com.steadyteller.backend.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginReturnsAccessTokenWhenPasswordMatches() {
        LoginRequest request = LoginRequest.builder()
                .email("member@example.com")
                .password("password123")
                .build();
        Member member = Member.create("member@example.com", "encoded", "steady");
        given(memberRepository.findByEmailAndStatus(request.getEmail(), MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(passwordEncoder.matches(request.getPassword(), member.getPassword())).willReturn(true);
        given(jwtTokenProvider.createAccessToken(member.getId())).willReturn("access-token");
        given(jwtTokenProvider.getAccessTokenExpirationSeconds()).willReturn(3600L);

        LoginResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresInSeconds()).isEqualTo(3600L);
    }

    @Test
    void loginRejectsInvalidPassword() {
        LoginRequest request = LoginRequest.builder()
                .email("member@example.com")
                .password("wrong-password")
                .build();
        Member member = Member.create("member@example.com", "encoded", "steady");
        given(memberRepository.findByEmailAndStatus(request.getEmail(), MemberStatus.ACTIVE))
                .willReturn(Optional.of(member));
        given(passwordEncoder.matches(request.getPassword(), member.getPassword())).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void loginDoesNotRevealWhetherEmailExists() {
        LoginRequest request = LoginRequest.builder()
                .email("unknown@example.com")
                .password("password123")
                .build();
        given(memberRepository.findByEmailAndStatus(request.getEmail(), MemberStatus.ACTIVE))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.INVALID_CREDENTIALS);
    }
}
