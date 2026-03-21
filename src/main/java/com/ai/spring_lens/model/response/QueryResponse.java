package com.ai.spring_lens.model.response;

import java.util.List;
import java.util.UUID;

public record QueryResponse(
        String answer,
        List<CitedSource> sources,
        Double confidence,
        UUID queryId,
        String retrievalStrategy,   // actual strategy used — reflects request param or config default
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long latencyMs
) {
    // Backward-compatible constructor — existing callers without token fields
    // Used by error fallback paths where token data is unavailable
    public QueryResponse(String answer, List<CitedSource> sources,
                         Double confidence, UUID queryId) {
        this(answer, sources, confidence, queryId, "unknown", 0, 0, 0, 0L);
    }

    public record CitedSource(
            String fileName,
            Integer pageNumber,
            String excerpt
    ) {}
}