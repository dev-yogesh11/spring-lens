package com.ai.spring_lens.service;

import com.ai.spring_lens.model.response.ChatResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class SpringAiChatService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiChatService.class);

    private final ChatClient chatClient;
    private final CircuitBreaker circuitBreaker;

    public SpringAiChatService(ChatClient.Builder builder,
                               CircuitBreakerRegistry circuitBreakerRegistry) {
        this.chatClient = builder
                .defaultSystem("You are SpringLens, a helpful AI assistant.")
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("groqClient");
    }

    public Mono<ChatResponse> chat(String message) {
        long start = System.currentTimeMillis();

        return Mono.fromCallable(() -> chatClient.prompt()
                        .user(message)
                        .call()
                        .chatResponse())
                .subscribeOn(Schedulers.boundedElastic())
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .map(response -> new ChatResponse(
                        response.getResult().getOutput().getText(),
                        response.getMetadata().getModel(),
                        response.getMetadata().getUsage().getPromptTokens(),
                        response.getMetadata().getUsage().getCompletionTokens(),
                        response.getMetadata().getUsage().getTotalTokens(),
                        System.currentTimeMillis() - start
                ))
                .onErrorResume(ex -> {
                    log.warn("Circuit breaker fallback triggered message={} reason={}",
                            message, ex.getMessage());
                    return Mono.just(new ChatResponse(
                            "Service temporarily unavailable. Please try again shortly.",
                            "fallback", 0, 0, 0, 0L
                    ));
                });
    }
}