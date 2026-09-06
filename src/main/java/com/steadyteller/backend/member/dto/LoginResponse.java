package com.steadyteller.backend.member.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private long expiresInSeconds;
    private MemberResponse member;
}
