package com.steadyteller.backend.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.steadyteller.backend.global.exception.GlobalExceptionHandler;
import com.steadyteller.backend.member.domain.MemberStatus;
import com.steadyteller.backend.member.dto.MemberResponse;
import com.steadyteller.backend.member.service.AuthService;
import com.steadyteller.backend.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private MemberService memberService;

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController authController = new AuthController(memberService, authService);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void signUpReturnsCommonApiResponse() throws Exception {
        MemberResponse response = MemberResponse.builder()
                .id(1L)
                .email("member@example.com")
                .nickname("steady")
                .status(MemberStatus.ACTIVE)
                .build();
        given(memberService.signUp(any())).willReturn(response);

        mockMvc.perform(post("/api/v1/auth/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "member@example.com",
                                  "password": "password123",
                                  "nickname": "steady"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("member@example.com"));
    }

    @Test
    void signUpReturnsValidationErrorForInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": "password123",
                                  "nickname": "steady"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.message").value("Invalid Input Value"));
        verifyNoInteractions(memberService);
    }
}
