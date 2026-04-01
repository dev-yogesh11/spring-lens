package com.ai.spring_lens.client.dto;

public record ProviderResponse(
        String content,
        String model,
        String provider,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long latencyMs
) {}