package com.ai.spring_lens.client;

import com.ai.spring_lens.client.dto.ProviderResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProviderClient {

    Mono<ProviderResponse> chat(
            String message,
            boolean useMemory,
            String conversationId
    );

    Flux<String> stream(String message);

    String name();
}