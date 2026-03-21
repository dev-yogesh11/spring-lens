package com.ai.spring_lens.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "springlens.cohere")
public class CohereRerankProperties {
    private String apiKey;
    private String baseUrl = "https://api.cohere.com";
    private String rerankModel = "rerank-v4.0-pro";
    private int topN = 4;
}
