package com.ai.spring_lens.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JDBC repository for audit event persistence.
 * Follows same pattern as HybridSearchRepository and RagasEvaluationRepository.
 *
 * Blocking JDBC — must be called on Schedulers.boundedElastic().
 * Caller (AuditService) handles scheduling via Mono.fromRunnable().
 */
@Slf4j
@Repository
public class AuditEventRepository {

    private static final String INSERT_AUDIT_EVENT = """
            INSERT INTO audit_events (
                id, tenant_id, user_id, query_hash,
                retrieval_strategy, sources_cited,
                prompt_tokens, completion_tokens, total_tokens,
                cost_usd, latency_ms, created_at
            ) VALUES (
                :id, :tenant_id, :user_id, :query_hash,
                :retrieval_strategy, :sources_cited,
                :prompt_tokens, :completion_tokens, :total_tokens,
                :cost_usd, :latency_ms, :created_at
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AuditEventRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts one audit event row.
     * Called fire-and-forget from AuditService — failure logged, never propagated.
     *
     * @param tenantId          tenant UUID from JWT
     * @param userId            user UUID from JWT
     * @param queryHash         SHA-256 hash of query string
     * @param retrievalStrategy strategy used for retrieval
     * @param sourcesCited      list of source filenames cited in response
     * @param promptTokens      LLM input token count
     * @param completionTokens  LLM output token count
     * @param totalTokens       total token count
     * @param costUsd           calculated cost in USD
     * @param latencyMs         end-to-end latency in milliseconds
     */
    public void save(UUID tenantId, UUID userId, String queryHash,
                     String retrievalStrategy, List<String> sourcesCited,
                     int promptTokens, int completionTokens, int totalTokens,
                     double costUsd, long latencyMs) {

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("tenant_id", tenantId)
                .addValue("user_id", userId)
                .addValue("query_hash", queryHash)
                .addValue("retrieval_strategy", retrievalStrategy)
                .addValue("sources_cited", sourcesCited == null ? null :
                        sourcesCited.toArray(new String[0]))
                .addValue("prompt_tokens", promptTokens)
                .addValue("completion_tokens", completionTokens)
                .addValue("total_tokens", totalTokens)
                .addValue("cost_usd", costUsd)
                .addValue("latency_ms", latencyMs)
                .addValue("created_at", LocalDateTime.now());

        jdbcTemplate.update(INSERT_AUDIT_EVENT, params);
        log.debug("Audit event saved: tenantId={} userId={} strategy={} cost={}",
                tenantId, userId, retrievalStrategy, costUsd);
    }
}