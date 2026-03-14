package com.ai.spring_lens.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GroqResponse(
        List<Choice> choices,
        Usage usage,
        String model
) {
    public record Choice(Message message) {
        public record Message(String role, String content) {}
    }

    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {}
}