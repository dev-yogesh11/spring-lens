-- ============================================================
-- V7__add_model_tracking.sql
-- Adds LLM and embedding model tracking for audit and quality.
--
-- Changes:
-- 1. audit_events → embedding_model, llm_provider, llm_model
-- 2. tenants      → embedding_model, embedding_dimensions
--
-- IDEMPOTENT:
-- Columns  → ADD COLUMN IF NOT EXISTS (PostgreSQL 9.6+)
-- Indexes  → CREATE INDEX IF NOT EXISTS (PostgreSQL 9.5+)
-- Backfill → UPDATE with WHERE guard — safe to run twice,
--            second run matches 0 rows and does nothing.
--
-- Why two tables:
-- audit_events : per-request model used for answering
--                (can vary per request / provider fallback)
-- tenants      : embedding model used at ingestion time
--                (must stay consistent — changing breaks vector search)
-- ============================================================


-- ── 1. audit_events — model used per request ─────────────────
-- REMOVED: embedding_model — derive via JOIN to tenants.embedding_model
-- KEPT: llm_provider and llm_model — these ARE request-specific,
--       tenants table has no equivalent for the answering model
ALTER TABLE audit_events
    ADD COLUMN IF NOT EXISTS llm_provider  VARCHAR(50)  DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS llm_model     VARCHAR(100) DEFAULT NULL;

-- Index for cost and quality analysis grouped by model over time.
-- CREATE INDEX IF NOT EXISTS is fully idempotent — skips silently if exists.
CREATE INDEX IF NOT EXISTS idx_audit_llm_model
    ON audit_events (llm_model, created_at DESC);


-- ── 2. tenants — embedding model locked at ingestion time ─────
-- embedding_model      : model name used when chunks were ingested
-- embedding_dimensions : vector dimensions — must match vector_store column
--
-- WARNING: changing embedding_model for an existing tenant
-- requires re-ingesting ALL documents — old and new vectors
-- are in incompatible spaces and cannot be mixed in similarity search.
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS embedding_model       VARCHAR(100) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS embedding_dimensions  INTEGER      DEFAULT NULL;


-- ── 3. Backfill default tenant ────────────────────────────────
-- text-embedding-ada-002 inferred from vector(1536) declared in V1.
-- WHERE guard on both columns ensures this is a true no-op on re-run —
-- if columns are already populated, zero rows are updated.
UPDATE tenants
SET embedding_model      = 'text-embedding-ada-002',
    embedding_dimensions = 1536
WHERE id = 'a0000000-0000-0000-0000-000000000001'
  AND embedding_model       IS NULL
  AND embedding_dimensions  IS NULL;
