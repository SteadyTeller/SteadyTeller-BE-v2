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

    @Size(min = 1, max = 50, message = "ëë¤ìì 1ì ì´ì 50ì ì´íì¬ì¼ í©ëë¤.")
    private String nickname;

    @Size(min = 8, max = 64, message = "ë¹ë°ë²í¸ë 8ì ì´ì 64ì ì´íì¬ì¼ í©ëë¤.")
    private String password;
}
