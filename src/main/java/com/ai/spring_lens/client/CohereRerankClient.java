package com.ai.spring_lens.client;

import com.ai.spring_lens.config.CohereRerankProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * WebClient-based client for Cohere Rerank API v2.
 *
 * Uses non-blocking WebClient — correct for WebFlux stack.
 * Cohere rerank is called reactively from HybridWithRerankRetrievalStrategy
 * which already runs on Schedulers.boundedElastic().
 *
 * API: POST https://api.cohere.com/v2/rerank
 * Auth: Bearer token via Authorization header
 * Model: rerank-v4.0-pro (cross-encoder — scores query+document pairs together)
 *
 * Cross-encoder vs bi-encoder:
 * Bi-encoder (vector search): query and document embedded separately,
 * cosine similarity compared. Fast, approximate.
 * Cross-encoder (Cohere rerank): query and document fed together into model,
 * relationship scored directly. Slower, significantly more accurate.
 */
@Slf4j
@Service
public class CohereRerankClient {

    private static final String RERANK_PATH = "/v2/rerank";

    private final WebClient webClient;
    private final CohereRerankProperties properties;

    public CohereRerankClient(WebClient.Builder webClientBuilder,
                               CohereRerankProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Calls Cohere Rerank API v2 synchronously.
     *
     * Returns ordered list of RerankResult — index references position
     * in the original documents list, relevanceScore is cross-encoder score.
     * Results are already ordered by relevance descending.
     *
     * Called from HybridWithRerankRetrievalStrategy which runs on
     * Schedulers.boundedElastic() — .block() is safe here.
     *
     * @param query     natural language query
     * @param documents list of document texts to rerank
     * @return ordered rerank results, most relevant first
     */
    public List<RerankResult> rerank(String query, List<String> documents) {
        log.debug("Cohere rerank: model={} query='{}' documents={}",
                properties.getRerankModel(), query, documents.size());

        RerankRequest request = new RerankRequest(
                properties.getRerankModel(),
                query,
                documents,
                properties.getTopN()
        );

        RerankResponse response = webClient.post()
                .uri(RERANK_PATH)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(body -> new RuntimeException(
                                        "Cohere API error status=" +
                                        clientResponse.statusCode() +
                                        " body=" + body)))
                .bodyToMono(RerankResponse.class)
                .block();

        if (response == null || response.results() == null) {
            log.warn("Cohere rerank returned null response for query='{}'", query);
            return List.of();
        }

        log.debug("Cohere rerank returned {} results for query='{}'",
                response.results().size(), query);

        return response.results();
    }

    // ---------------------------------------------------------------
    // Request / Response DTOs — match Cohere v2 rerank API exactly
    // Field names use camelCase — Jackson maps to snake_case via
    // @JsonProperty annotations where API expects snake_case
    // ---------------------------------------------------------------

    /**
     * POST body for /v2/rerank
     * model: rerank-v4.0-pro
     * query: user's natural language question
     * documents: list of chunk texts from RRF retrieval
     * top_n: how many results to return after reranking
     */
    record RerankRequest(
            String model,
            String query,
            List<String> documents,
            @com.fasterxml.jackson.annotation.JsonProperty("top_n") int topN
    ) {}

    /**
     * Response wrapper from /v2/rerank
     * results: ordered list of reranked documents
     */
    record RerankResponse(
            List<RerankResult> results
    ) {}

    /**
     * Individual rerank result.
     * index: position in the original documents list sent to Cohere
     * relevance_score: cross-encoder relevance score (0.0 to 1.0)
     * Higher score = more relevant to the query.
     */
    public record RerankResult(
            int index,
            @com.fasterxml.jackson.annotation.JsonProperty("relevance_score")
            double relevanceScore
    ) {}
}
