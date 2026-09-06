package com.steadyteller.backend.member.dto;

import com.steadyteller.backend.member.domain.Member;
import com.steadyteller.backend.member.domain.MemberStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberResponse {

    private Long id;
    private String email;
    private String nickname;
    private MemberStatus status;
    private LocalDateTime createdAt;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .status(member.getStatus())
                .createdAt(member.getCreatedAt())
                .build();
    }
}
