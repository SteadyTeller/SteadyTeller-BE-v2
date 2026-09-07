package com.steadyteller.backend.global.security.jwt;

import com.steadyteller.backend.global.exception.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증되지 않은 상태로 보호된 API에 접근했을 때 401 응답을 ErrorResponse와 동일한 포맷으로 내려준다.
 * Spring Security 필터 단계에서 발생하는 예외라 GlobalExceptionHandler(@RestControllerAdvice)를 타지 않으므로,
 * ObjectMapper 빈에 의존하지 않고 직접 JSON을 작성한다 (ErrorResponse는 code/message 두 필드뿐이라 충분히 단순함).
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        boolean invalidToken = "INVALID_TOKEN".equals(request.getAttribute(JwtAuthenticationFilter.JWT_ERROR_ATTRIBUTE));
        GlobalErrorCode errorCode = invalidToken ? GlobalErrorCode.INVALID_TOKEN : GlobalErrorCode.UNAUTHORIZED;

        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":\"%s\",\"message\":\"%s\"}".formatted(errorCode.getCode(), errorCode.getMessage()));
    }
}
