package com.steadyteller.backend.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
public class MemberSignUpRequest {

    @NotBlank(message = "ì´ë©ì¼ì íììëë¤.")
    @Email(message = "ì¬ë°ë¥¸ ì´ë©ì¼ íìì´ ìëëë¤.")
    private String email;

    @NotBlank(message = "ë¹ë°ë²í¸ë íììëë¤.")
    @Size(min = 8, max = 64, message = "ë¹ë°ë²í¸ë 8ì ì´ì 64ì ì´íì¬ì¼ í©ëë¤.")
    private String password;

    @NotBlank(message = "ëë¤ìì íììëë¤.")
    @Size(max = 50, message = "ëë¤ìì 50ì ì´íì¬ì¼ í©ëë¤.")
    private String nickname;
}
