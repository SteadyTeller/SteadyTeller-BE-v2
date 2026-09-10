package com.steadyteller.backend.global.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 전역 공통 설정.
 * 태스크 생성(LearningTask), 스케줄 생성(Schedule) 등 AI 기능이 필요한
 * 모든 도메인에서 공용으로 사용할 ChatClient 빈을 제공한다.
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }
}
