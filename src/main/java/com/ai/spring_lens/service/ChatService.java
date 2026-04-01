package com.ai.spring_lens.service;

import com.ai.spring_lens.client.GroqClient;
import com.ai.spring_lens.model.response.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ChatService {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are SpringLens, a helpful AI assistant. " +
            "Answer questions clearly and concisely. " +
            "Always be honest when you don't know something.";

    private final GroqClient groqClient;

    public ChatService(GroqClient groqClient) {
        this.groqClient = groqClient;
    }

    public Mono<ChatResponse> chat(String userMessage) {
        long startTime = System.currentTimeMillis();

        return groqClient.chat(userMessage, DEFAULT_SYSTEM_PROMPT)
                .map(groqResponse -> {
                    long latencyMs = System.currentTimeMillis() - startTime;
                    String content = groqResponse.choices()
                            .get(0)
                            .message()
                            .content();

                    return new ChatResponse(
                            content,
                            "DEFAULT",
                            groqResponse.model(),
                            groqResponse.usage().promptTokens(),
                            groqResponse.usage().completionTokens(),
                            groqResponse.usage().totalTokens(),
                            latencyMs
                    );
                });
    }
}