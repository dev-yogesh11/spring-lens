package com.ai.spring_lens.model.response;

public record ChatResponse(
        String response,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        long latencyMs
) {}