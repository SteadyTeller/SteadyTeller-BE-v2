package com.steadyteller.backend.member.dto;

import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberUpdateRequest {

    @Size(min = 1, max = 50, message = "닉네임은 1자 이상 50자 이하여야 합니다.")
    private String nickname;

    @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
    private String password;
}
