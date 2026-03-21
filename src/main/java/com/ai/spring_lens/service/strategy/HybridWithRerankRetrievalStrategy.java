package com.ai.spring_lens.service.strategy;

import com.ai.spring_lens.client.CohereRerankClient;
import com.ai.spring_lens.client.CohereRerankClient.RerankResult;
import com.ai.spring_lens.config.CohereRerankProperties;
import com.ai.spring_lens.config.IngestionProperties;
import com.ai.spring_lens.service.ReciprocalRankFusionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hybrid search + Cohere cross-encoder reranking — Phase 2 Week 12.
 *
 * Pipeline:
 * Step 1: RRF retrieves top candidates (vector + FTS merged)
 * Step 2: Cohere Rerank v2 scores each candidate against query
 *         using cross-encoder model (rerank-v4.0-pro)
 * Step 3: Returns top-n by Cohere relevance score
 *
 * Bean name "hybrid-rerank" matches ChatRequest.retrievalStrategy value
 * and springlens.retrieval.default-strategy config value.
 *
 * Why reranking improves over hybrid-only:
 * RRF bi-encoder retrieval: fast, approximate — embeds query and
 * document independently, misses nuanced relevance.
 * Cohere cross-encoder: query and document scored together —
 * understands full context of the relationship. More accurate,
 * runs only on top RRF candidates so latency is acceptable.
 *
 * Analogy from LEARNINGS.md:
 * Bi-encoder = shortlisting by resume keywords (fast, approximate)
 * Cross-encoder = interviewing shortlisted candidates (slow, accurate)
 */
@Slf4j
@Component("hybrid-rerank")
public class HybridWithRerankRetrievalStrategy implements RetrievalStrategy {

    private final ReciprocalRankFusionService rrfService;
    private final CohereRerankClient cohereRerankClient;
    private final CohereRerankProperties cohereProperties;

    public HybridWithRerankRetrievalStrategy(ReciprocalRankFusionService rrfService,
                                              CohereRerankClient cohereRerankClient,
                                              CohereRerankProperties cohereProperties) {
        this.rrfService = rrfService;
        this.cohereRerankClient = cohereRerankClient;
        this.cohereProperties = cohereProperties;
    }

    @Override
    public List<Document> retrieve(String query, double similarityThreshold) {
        log.debug("HybridRerank retrieval for query='{}'", query);

        // Step 1: RRF retrieves top candidates — vector + FTS merged
        // Uses HybridSearchProperties.finalTopK as candidate pool
        List<Document> rrfCandidates = rrfService.hybridSearch(
                query, similarityThreshold
        );

        log.debug("RRF candidates={} for query='{}'",
                rrfCandidates.size(), query);

        // Edge case: if RRF returns nothing, return empty — no point reranking
        if (rrfCandidates.isEmpty()) {
            log.debug("RRF returned 0 candidates — skipping rerank for query='{}'",
                    query);
            return List.of();
        }

        // Edge case: if only one candidate, skip rerank — nothing to reorder
        if (rrfCandidates.size() == 1) {
            log.debug("Single RRF candidate — skipping rerank for query='{}'",
                    query);
            return rrfCandidates;
        }

        // Step 2: extract text from each candidate for Cohere
        // Cohere expects plain text strings — not Document objects
        List<String> documentTexts = rrfCandidates.stream()
                .map(Document::getText)
                .toList();

        // Step 3: call Cohere Rerank API
        // Returns results ordered by relevance_score descending
        // result.index() references position in documentTexts list
        List<RerankResult> rerankResults = cohereRerankClient.rerank(
                query, documentTexts
        );

        // Edge case: if Cohere returns empty (API error handled gracefully),
        // fall back to RRF ordering — better than returning nothing
        if (rerankResults.isEmpty()) {
            log.warn("Cohere rerank returned empty — falling back to RRF order " +
                    "for query='{}'", query);
            return rrfCandidates.stream()
                    .limit(cohereProperties.getTopN())
                    .toList();
        }

        // Step 4: reorder RRF candidates by Cohere relevance score
        // rerankResults already ordered by score descending
        // result.index() maps back to original rrfCandidates position
        List<Document> reranked = new ArrayList<>();
        for (RerankResult result : rerankResults) {
            if (result.index() < rrfCandidates.size()) {
                Document doc = rrfCandidates.get(result.index());
                reranked.add(doc);
                log.debug("Reranked doc index={} score={} file={}",
                        result.index(),
                        String.format("%.4f", result.relevanceScore()),
                        doc.getMetadata().getOrDefault("original_file_name", "unknown"));
            }
        }

        log.debug("HybridRerank returning {} documents for query='{}'",
                reranked.size(), query);

        return reranked;
    }
}
