package com.ai.spring_lens.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Data
@ConfigurationProperties(prefix = "springlens.llm")
public class LlmProperties {

    private Provider groq;
    private Provider openai;
    private Provider ollama;

    @Data
    public static class Provider {
        private String baseUrl;
        private String apiKey;
        private String model;
        private Duration timeout;

    }


}