package com.ai.spring_lens.config;

import org.springframework.boot.http.codec.CodecCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Bean
    public CodecCustomizer codecCustomizer() {
        return configurer -> configurer.defaultCodecs()
                .maxInMemorySize(10 * 1024 * 1024); // 10MB
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}