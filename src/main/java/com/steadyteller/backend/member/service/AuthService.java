package com.steadyteller.backend.member.service;

import com.steadyteller.backend.global.exception.CustomException;
import com.steadyteller.backend.global.security.JwtTokenProvider;
import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.domain.MemberStatus;
import com.steadyteller.backend.member.dto.LoginRequest;
import com.steadyteller.backend.member.dto.LoginResponse;
import com.steadyteller.backend.member.dto.MemberResponse;
import com.steadyteller.backend.member.exception.MemberErrorCode;
import com.steadyteller.backend.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmailAndStatus(request.getEmail(), MemberStatus.ACTIVE)
                .orElseThrow(() -> new CustomException(MemberErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new CustomException(MemberErrorCode.INVALID_CREDENTIALS);
        }

        return LoginResponse.builder()
                .accessToken(jwtTokenProvider.createAccessToken(member.getId()))
                .tokenType("Bearer")
                .expiresInSeconds(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .member(MemberResponse.from(member))
                .build();
    }
}
