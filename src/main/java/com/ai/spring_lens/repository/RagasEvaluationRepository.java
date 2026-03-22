package com.ai.spring_lens.repository;

import com.ai.spring_lens.model.ragas.RagasEvaluationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Repository
public class RagasEvaluationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    // ── Insert aggregate run scores ───────────────────────────
    private static final String INSERT_RUN = """
            INSERT INTO ragas_evaluation_run (
                id, retrieval_strategy, pair_count,
                faithfulness, answer_relevancy,
                context_precision, context_recall,
                evaluated_at, created_at
            ) VALUES (
                :id, :retrieval_strategy, :pair_count,
                :faithfulness, :answer_relevancy,
                :context_precision, :context_recall,
                :evaluated_at, :created_at
            )
            """;

    // ── Insert one per-pair score row ─────────────────────────
    private static final String INSERT_PAIR = """
            INSERT INTO ragas_evaluation_pair (
                id, run_id, question,
                faithfulness, answer_relevancy,
                context_precision, context_recall,
                created_at
            ) VALUES (
                :id, :run_id, :question,
                :faithfulness, :answer_relevancy,
                :context_precision, :context_recall,
                :created_at
            )
            """;

    // ── Dashboard query: avg scores by strategy over last N days ─
    private static final String AVG_SCORES_BY_STRATEGY = """
            SELECT
                retrieval_strategy,
                COUNT(*)                    AS run_count,
                AVG(faithfulness)           AS avg_faithfulness,
                AVG(answer_relevancy)       AS avg_answer_relevancy,
                AVG(context_precision)      AS avg_context_precision,
                AVG(context_recall)         AS avg_context_recall,
                MAX(evaluated_at)           AS last_evaluated_at
            FROM ragas_evaluation_run
            WHERE evaluated_at >= now() - (:days * INTERVAL '1 day')
            GROUP BY retrieval_strategy
            ORDER BY avg_faithfulness DESC
            """;

    // ── Regression alert query: avg faithfulness for a strategy over N days ─
    private static final String AVG_FAITHFULNESS_BY_STRATEGY = """
        SELECT AVG(faithfulness) AS avg_faithfulness
        FROM ragas_evaluation_run
        WHERE retrieval_strategy = :strategy
        AND evaluated_at >= now() - (:days * INTERVAL '1 day')
        """;

    public RagasEvaluationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Saves one evaluation run and all its per-pair scores to the database.
     * Called automatically after every successful POST /evaluate.
     *
     * Blocking JDBC — must be called on Schedulers.boundedElastic()
     * from the reactive chain in RagasEvaluationService.
     *
     * @param response the full RAGAS evaluation response from Python service
     * @return UUID of the saved run
     */
    public UUID saveEvaluation(RagasEvaluationResponse response) {
        UUID runId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        // ── Save aggregate run ────────────────────────────────
        MapSqlParameterSource runParams = new MapSqlParameterSource()
                .addValue("id", runId)
                .addValue("retrieval_strategy", response.retrievalStrategy())
                .addValue("pair_count", response.pairCount())
                .addValue("faithfulness", response.scores().faithfulness())
                .addValue("answer_relevancy", response.scores().answerRelevancy())
                .addValue("context_precision", response.scores().contextPrecision())
                .addValue("context_recall", response.scores().contextRecall())
                .addValue("evaluated_at", now)
                .addValue("created_at", now);

        jdbcTemplate.update(INSERT_RUN, runParams);
        log.debug("Saved evaluation run: id={} strategy={}",
                runId, response.retrievalStrategy());

        // ── Batch save per-pair scores ────────────────────────
        if (response.perPairScores() != null && !response.perPairScores().isEmpty()) {
            MapSqlParameterSource[] pairParams = response.perPairScores().stream()
                    .map(pair -> new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID())
                            .addValue("run_id", runId)
                            .addValue("question", pair.question())
                            .addValue("faithfulness", pair.faithfulness())
                            .addValue("answer_relevancy", pair.answerRelevancy())
                            .addValue("context_precision", pair.contextPrecision())
                            .addValue("context_recall", pair.contextRecall())
                            .addValue("created_at", now))
                    .toArray(MapSqlParameterSource[]::new);

            jdbcTemplate.batchUpdate(INSERT_PAIR, pairParams);
            log.debug("Saved {} evaluation pairs for run={}",
                    pairParams.length, runId);
        }

        return runId;
    }

    /**
     * Returns average RAGAS scores grouped by retrieval strategy
     * for all runs within the last N days.
     *
     * Used by GET /api/v1/admin/quality?days=N dashboard endpoint.
     *
     * Blocking JDBC — must be called on Schedulers.boundedElastic().
     *
     * @param days number of days to look back
     * @return list of strategy summary records
     */
    public List<StrategySummary> findAverageScoresByStrategy(int days) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("days", days);

        return jdbcTemplate.query(AVG_SCORES_BY_STRATEGY, params, (rs, rowNum) ->
                new StrategySummary(
                        rs.getString("retrieval_strategy"),
                        rs.getInt("run_count"),
                        rs.getDouble("avg_faithfulness"),
                        rs.getDouble("avg_answer_relevancy"),
                        rs.getDouble("avg_context_precision"),
                        rs.getDouble("avg_context_recall"),
                        rs.getTimestamp("last_evaluated_at").toLocalDateTime()
                )
        );
    }

    /**
     * Returns average faithfulness score for a specific retrieval strategy
     * over the last N days. Used by regression alert check.
     *
     * Returns empty if no runs exist for this strategy in the window.
     * Blocking JDBC — must be called on Schedulers.boundedElastic().
     *
     * @param strategy retrieval strategy name
     * @param days     lookback window in days
     * @return average faithfulness score or empty if no data
     */
    public java.util.Optional<Double> findAverageFaithfulness(String strategy, int days) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("strategy", strategy)
                .addValue("days", days);

        Double avg = jdbcTemplate.queryForObject(
                AVG_FAITHFULNESS_BY_STRATEGY, params, Double.class);

        return java.util.Optional.ofNullable(avg);
    }

    /**
     * Summary record for one retrieval strategy — used by quality dashboard.
     */
    public record StrategySummary(
            String retrievalStrategy,
            int runCount,
            double avgFaithfulness,
            double avgAnswerRelevancy,
            double avgContextPrecision,
            double avgContextRecall,
            LocalDateTime lastEvaluatedAt
    ) {}
}