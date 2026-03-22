package com.ai.spring_lens.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable LLM token pricing for cost calculation.
 *
 * Prices are per million tokens — matches OpenAI pricing page format.
 * Change these values when switching LLM providers or when pricing changes.
 * Zero code change required — config only.
 *
 * Current values (GPT-4o-mini as of 2026):
 * input:  $0.15 per million tokens
 * output: $0.60 per million tokens
 *
 * Formula:
 * cost = (promptTokens / 1_000_000 * inputPricePerMillion)
 *      + (completionTokens / 1_000_000 * outputPricePerMillion)
 */
@Data
@Component
@ConfigurationProperties(prefix = "springlens.cost")
public class CostProperties {
    private double inputTokenPricePerMillion = 0.15;
    private double outputTokenPricePerMillion = 0.60;

    /**
     * Calculates cost in USD for a single LLM call.
     *
     * @param promptTokens     number of input tokens
     * @param completionTokens number of output tokens
     * @return cost in USD rounded to 6 decimal places
     */
    public double calculate(int promptTokens, int completionTokens) {
        double cost = (promptTokens / 1_000_000.0 * inputTokenPricePerMillion)
                + (completionTokens / 1_000_000.0 * outputTokenPricePerMillion);
        return Math.round(cost * 1_000_000.0) / 1_000_000.0;
    }
}