package com.ai.spring_lens.client.dto;

public record ProviderResponse(
        String content,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs
) {}