package com.steadyteller.backend.member.controller;

import com.steadyteller.backend.global.common.ApiResponse;
import com.steadyteller.backend.member.dto.LoginRequest;
import com.steadyteller.backend.member.dto.LoginResponse;
import com.steadyteller.backend.member.dto.MemberResponse;
import com.steadyteller.backend.member.dto.MemberSignUpRequest;
import com.steadyteller.backend.member.service.MemberService;
import com.steadyteller.backend.member.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final MemberService memberService;
    private final AuthService authService;

    @PostMapping("/members")
    public ResponseEntity<ApiResponse<MemberResponse>> signUp(
            @Valid @RequestBody MemberSignUpRequest request
    ) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                "회원가입이 완료되었습니다.",
                memberService.signUp(request)
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "로그인에 성공했습니다.",
                authService.login(request)
        ));
    }
}
