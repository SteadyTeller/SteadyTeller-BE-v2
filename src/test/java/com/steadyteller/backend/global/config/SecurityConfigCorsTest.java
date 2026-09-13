package com.steadyteller.backend.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인증이 필요한 API로 보내는 CORS 프리플라이트(OPTIONS) 요청이 인증 필터에 막히지 않고
 * 허용된 오리진에는 Access-Control-Allow-Origin 헤더가 실리는지, 허용되지 않은 오리진은
 * 차단되는지 확인한다. dev 프로필의 기본 허용 오리진은 http://localhost:5173 이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SecurityConfigCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightFromAllowedOriginPassesAuthAndReceivesCorsHeader() throws Exception {
        mockMvc.perform(options("/api/v1/members/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void preflightFromDisallowedOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/members/me")
                        .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
