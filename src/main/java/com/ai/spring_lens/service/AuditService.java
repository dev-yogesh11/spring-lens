package com.ai.spring_lens.service;

import com.ai.spring_lens.config.CostProperties;
import com.ai.spring_lens.repository.AuditEventRepository;
import com.ai.spring_lens.security.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Audit logging service — records every user query for cost tracking
 * and compliance visibility.
 *
 * Fire-and-forget pattern — audit failure never blocks user response.
 * All methods return Mono<Void> — caller chains with .then() but
 * errors are swallowed via onErrorResume.
 *
 * Runs on Schedulers.boundedElastic() — JDBC is blocking.
 *
 * Query is stored as SHA-256 hash — never plain text.
 * This protects potentially sensitive query content while still
 * allowing deduplication and frequency analysis.
 */
@Slf4j
@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final CostProperties costProperties;

    public AuditService(AuditEventRepository auditEventRepository,
                        CostProperties costProperties) {
        this.auditEventRepository = auditEventRepository;
        this.costProperties = costProperties;
    }

    /**
     * Records a completed query audit event.
     * Called after query() and chat() complete successfully.
     *
     * @param tenantContext      tenant and user identity from JWT
     * @param query              original query string — hashed before storage
     * @param retrievalStrategy  strategy used for retrieval
     * @param sourcesCited       list of source filenames from response
     * @param promptTokens       LLM input token count
     * @param completionTokens   LLM output token count
     * @param totalTokens        total token count
     * @param latencyMs          end-to-end latency in milliseconds
     * @return empty Mono — fire and forget
     */
    public Mono<Void> recordQuery(TenantContext tenantContext,
                                   String query,
                                   String retrievalStrategy,
                                   List<String> sourcesCited,
                                   int promptTokens,
                                   int completionTokens,
                                   int totalTokens,
                                   long latencyMs) {
        return Mono.fromRunnable(() -> {
            String queryHash = hashQuery(query);
            double costUsd = costProperties.calculate(promptTokens, completionTokens);

            auditEventRepository.save(
                    tenantContext.tenantId(),
                    tenantContext.userId(),
                    queryHash,
                    retrievalStrategy,
                    sourcesCited,
                    promptTokens,
                    completionTokens,
                    totalTokens,
                    costUsd,
                    latencyMs
            );

            log.info("Audit recorded: tenantId={} userId={} strategy={} " +
                            "tokens={} cost=${} latencyMs={}",
                    tenantContext.tenantId(),
                    tenantContext.userId(),
                    retrievalStrategy,
                    totalTokens,
                    String.format("%.6f", costUsd),
                    latencyMs);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnError(ex -> log.warn(
                "Audit recording failed — response not affected: " +
                "tenantId={} reason={}",
                tenantContext.tenantId(), ex.getMessage()))
        .onErrorResume(ex -> Mono.empty())
        .then();
    }

    /**
     * Records a stream audit event.
     * Token counts are 0 for streaming — Spring AI does not return
     * usage metadata reliably for streaming responses.
     *
     * @param tenantContext     tenant and user identity from JWT
     * @param query             original query string — hashed before storage
     * @param retrievalStrategy strategy used for retrieval
     * @param latencyMs         time until first token streamed
     * @return empty Mono — fire and forget
     */
    public Mono<Void> recordStream(TenantContext tenantContext,
                                    String query,
                                    String retrievalStrategy,
                                    long latencyMs) {
        return recordQuery(
                tenantContext,
                query,
                retrievalStrategy,
                List.of(),
                0, 0, 0,
                latencyMs
        );
    }

    /**
     * SHA-256 hashes the query string.
     * Protects potentially sensitive query content in audit log.
     * Allows deduplication and frequency analysis without storing raw text.
     */
    private String hashQuery(String query) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    query.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available — using raw query length as hash");
            return String.valueOf(query.length());
        }
    }
}