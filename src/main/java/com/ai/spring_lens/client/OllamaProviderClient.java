package com.ai.spring_lens.client;

import com.ai.spring_lens.client.dto.ProviderResponse;
import com.ai.spring_lens.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OllamaProviderClient implements ProviderClient {

    private final WebClient webClient;
    private final LlmProperties properties;

    public OllamaProviderClient(LlmProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getOllama().getBaseUrl())
                .build();
    }

    @Override
    public Mono<ProviderResponse> chat(
            String message,
            boolean useMemory,
            String conversationId
    ) {
        long start = System.currentTimeMillis();

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(Map.of(
                        "model", properties.getOllama().getModel(),
                        "messages", List.of(
                                Map.of("role", "user", "content", message)
                        ),
                        "stream", false
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Map choice = ((List<Map>) response.get("choices")).get(0);
                    Map messageMap = (Map) choice.get("message");

                    return new ProviderResponse(
                            (String) messageMap.get("content"),
                            "ollama-" + properties.getOllama().getModel(),
                            0, 0, 0,
                            System.currentTimeMillis() - start
                    );
                })
                .timeout(properties.getOllama().getTimeout())
                .doOnSubscribe(s -> log.info("Calling OLLAMA"))
                .doOnSuccess(r -> log.info("OLLAMA success latency={}ms", r.latencyMs()))
                .doOnError(e -> log.warn("OLLAMA failed: {}", e.getMessage()));
    }

    @Override
    public Flux<String> stream(String message) {
        return null;
    }
/*
    @Override
    public Flux<String> stream(String message) {
        return chatClient.prompt().user(message).stream().content();
    }*/

    @Override
    public String name() {
        return "OLLAMA";
    }
}