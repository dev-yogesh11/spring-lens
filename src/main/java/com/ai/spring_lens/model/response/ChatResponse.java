package com.ai.spring_lens.model.response;

public record ChatResponse(
        String response,
        String model,
        String provider,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        long latencyMs
) {}