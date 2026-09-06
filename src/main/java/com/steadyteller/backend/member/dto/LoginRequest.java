package com.steadyteller.backend.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LoginRequest {

    @NotBlank(message = "ì´ë©ì¼ì íììëë¤.")
    @Email(message = "ì¬ë°ë¥¸ ì´ë©ì¼ íìì´ ìëëë¤.")
    private String email;

    @NotBlank(message = "ë¹ë°ë²í¸ë íììëë¤.")
    private String password;
}
