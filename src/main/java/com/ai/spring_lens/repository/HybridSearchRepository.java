package com.ai.spring_lens.repository;

import com.ai.spring_lens.config.HybridSearchProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Repository
public class HybridSearchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final HybridSearchProperties properties;

    // SQL for PostgreSQL full-text search using tsvector column added in V2 migration.
    // plainto_tsquery converts plain text query to tsquery — handles stop words,
    // stemming, and multi-word queries without requiring special syntax from caller.
    // ts_rank scores each row by how well it matches the query.
    // Results ordered by rank descending — highest relevance first.
    // Change the SQL constant — add tenant_id filter
    private static final String FTS_QUERY = """
        SELECT id::text,
               content,
               metadata::text,
               ts_rank(fts_content, plainto_tsquery('english', :query)) AS rank
        FROM vector_store
        WHERE fts_content @@ plainto_tsquery('english', :query)
          AND tenant_id = :tenantId::uuid
        ORDER BY rank DESC
        LIMIT :limit
        """;

    private static final String DUPLICATE_CHECK_QUERY = """
    SELECT COUNT(*) FROM vector_store
    WHERE metadata->>'original_file_name' = :originalFileName
      AND metadata->>'tenant_id' = :tenantId
    """;

    public HybridSearchRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                   HybridSearchProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /**
     * Executes PostgreSQL full-text search against the fts_content tsvector column.
     * Returns up to ftsTopK results ranked by ts_rank score.
     *
     * Called from ReciprocalRankFusionService on Schedulers.boundedElastic() —
     * this is a blocking JDBC call and must never be called on Netty event loop thread.
     *
     * @param query natural language query string
     * @return list of FtsResult records ordered by rank descending
     */
    public List<FtsResult> fullTextSearch(String query, UUID tenantId) {
        log.debug("FTS query: '{}'", query);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("query", query)
                .addValue("tenantId", tenantId)
                .addValue("limit", properties.getFtsTopK());

        List<FtsResult> results = jdbcTemplate.query(FTS_QUERY, params, (rs, rowNum) ->
                new FtsResult(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("content"),
                        rs.getString("metadata"),
                        rs.getDouble("rank")
                )
        );

        log.debug("FTS returned {} results for query='{}'", results.size(), query);
        return results;
    }

    /**
     * Checks whether a document with the given file name already exists for the tenant.
     * Uses a direct JDBC metadata query against the vector_store table instead of
     * similarity search — existence is a metadata question, not a semantic one.
     *
     * @param originalFileName the original uploaded file name to check for duplicates
     * @param tenantId         the tenant identifier used to scope the check
     * @return true if a document with the same file name exists for the tenant, false otherwise
     */
    public boolean existsByFileNameAndTenant(String originalFileName, String tenantId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("originalFileName", originalFileName)
                .addValue("tenantId", tenantId);

        Integer count = jdbcTemplate.queryForObject(DUPLICATE_CHECK_QUERY, params, Integer.class);
        return count != null && count > 0;
    }
    /**
     * Immutable result record from full-text search.
     * id matches vector_store primary key — used for deduplication in RRF merge.
     * rank is ts_rank score — used for RRF position calculation.
     * metadata stored as raw JSON string — parsed by RRF service if needed.
     */
    public record FtsResult(
            UUID id,
            String content,
            String metadata,
            double rank
    ) {}
}
