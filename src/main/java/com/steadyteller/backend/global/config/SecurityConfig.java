package com.steadyteller.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // REST API이므로 CSRF 보안 비활성화
                .csrf(AbstractHttpConfigurer::disable)
                
                // 기본 폼 로그인 및 Basic Auth 비활성화 (브라우저 기본 로그인 창 뜨는 것 방지)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                
                // JWT를 사용할 것이므로 세션 상태를 STATELESS로 설정
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // H2 콘솔 접근을 위한 설정 (Iframe 허용)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                
                // 권한 규칙 설정
                .authorizeHttpRequests(auth -> auth
                        // Swagger UI 및 H2 콘솔은 인증 없이 누구나 접근 가능하도록 허용
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/h2-console/**",
                                "/error"
                        ).permitAll()
                        // 그 외의 모든 API 요청은 (일단 지금은) 모두 허용. 나중에 JWT 필터 적용 시 변경
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
