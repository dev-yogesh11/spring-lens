package com.ai.spring_lens.model.response;

public record ErrorResponse(
        String error,
        String message
) {}