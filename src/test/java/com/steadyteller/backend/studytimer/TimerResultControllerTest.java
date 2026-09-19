package com.steadyteller.backend.studytimer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TimerResultControllerTest {

    @Mock
    private TimerResultService service;

    @InjectMocks
    private TimerResultController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savesTimerResultForAuthenticatedMember() throws Exception {
        UUID attemptId = UUID.randomUUID();
        TimerResultRequest request = new TimerResultRequest(
                attemptId, 35, StudyResult.COMPLETED, null, null, "정규화 학습", null);
        TimerResult stored = TimerResult.create(
                7L, 10L, 20L, new TimerScheduleQueryPort.Context(30L, 40L, "정규화", 45), request);
        when(service.save(any(), any(), any(), any())).thenReturn(stored);

        mockMvc.perform(post("/api/v1/schedules/10/items/20/timer-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "attemptId": "%s",
                                  "actualMinutes": 35,
                                  "result": "COMPLETED",
                                  "learnedContent": "정규화 학습"
                                }
                                """.formatted(attemptId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scheduleId").value(10))
                .andExpect(jsonPath("$.data.scheduleItemId").value(20))
                .andExpect(jsonPath("$.data.actualMinutes").value(35));

        verify(service).save(any(), any(), any(), any());
    }

    @Test
    void returnsOwnedItemTimerHistory() throws Exception {
        TimerResultResponse response = new TimerResultResponse(
                1L, UUID.randomUUID(), 10L, 20L, 30L, 40L, "정규화",
                45, 35, StudyResult.COMPLETED, null, null, "정규화 학습", null, null);
        when(service.findHistory(7L, 10L, 20L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/schedules/10/items/20/timer-results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].learningTaskId").value(40))
                .andExpect(jsonPath("$.data[0].learnedContent").value("정규화 학습"));

        verify(service).findHistory(7L, 10L, 20L);
    }
}
