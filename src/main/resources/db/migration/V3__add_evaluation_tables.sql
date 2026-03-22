-- ============================================================
-- V3__add_evaluation_tables.sql
-- Adds RAGAS evaluation result storage tables.
-- Supports quality dashboard and regression alert features.
--
-- IDEMPOTENT: Safe to run multiple times — all statements use
-- IF NOT EXISTS. Flyway prevents re-execution by version tracking
-- but this script is safe even if run manually via psql.
--
-- Tables:
-- 1. ragas_evaluation_run  — one row per evaluation run (aggregate scores)
-- 2. ragas_evaluation_pair — one row per Q&A pair per run (per-question scores)
--
-- Relationship: ragas_evaluation_run (1) → (many) ragas_evaluation_pair
-- ============================================================

-- ── Table 1: One row per POST /evaluate call ─────────────────
CREATE TABLE IF NOT EXISTS ragas_evaluation_run (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid(),
    retrieval_strategy   VARCHAR(50) NOT NULL,
    pair_count           INTEGER     NOT NULL,
    faithfulness         NUMERIC(6,4),
    answer_relevancy     NUMERIC(6,4),
    context_precision    NUMERIC(6,4),
    context_recall       NUMERIC(6,4),
    evaluated_at         TIMESTAMP   NOT NULL DEFAULT now(),
    created_at           TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT ragas_evaluation_run_pkey PRIMARY KEY (id)
);

-- Index for dashboard query: avg scores over last N days by strategy
CREATE INDEX IF NOT EXISTS idx_ragas_run_strategy_evaluated
    ON ragas_evaluation_run (retrieval_strategy, evaluated_at DESC);

-- Index for regression alert: recent runs ordered by time
CREATE INDEX IF NOT EXISTS idx_ragas_run_evaluated_at
    ON ragas_evaluation_run (evaluated_at DESC);

-- ── Table 2: One row per Q&A pair per run ────────────────────
CREATE TABLE IF NOT EXISTS ragas_evaluation_pair (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid(),
    run_id               UUID        NOT NULL,
    question             TEXT        NOT NULL,
    faithfulness         NUMERIC(6,4),
    answer_relevancy     NUMERIC(6,4),
    context_precision    NUMERIC(6,4),
    context_recall       NUMERIC(6,4),
    created_at           TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT ragas_evaluation_pair_pkey PRIMARY KEY (id),
    CONSTRAINT fk_ragas_pair_run
        FOREIGN KEY (run_id)
        REFERENCES ragas_evaluation_run (id)
        ON DELETE CASCADE
);

-- Index for fetching all pairs belonging to a specific run
CREATE INDEX IF NOT EXISTS idx_ragas_pair_run_id
    ON ragas_evaluation_pair (run_id);

-- Index for per-question analysis across all runs
CREATE INDEX IF NOT EXISTS idx_ragas_pair_question
    ON ragas_evaluation_pair
    USING gin(to_tsvector('english', question));
