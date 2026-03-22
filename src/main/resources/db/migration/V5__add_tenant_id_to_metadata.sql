-- ============================================================
-- V5__add_tenant_id_to_metadata.sql
-- Adds tenant_id into metadata JSON column of vector_store.
--
-- Spring AI filterExpression works on metadata JSON — not on
-- separate columns. This migration backfills tenant_id into
-- metadata so filterExpression("tenant_id == 'uuid'") works.
--
-- All 795 existing chunks belong to default tenant:
-- a0000000-0000-0000-0000-000000000001
--
-- IDEMPOTENT: Uses || operator which overwrites existing
-- tenant_id value if already present — safe to run twice.
-- ============================================================

UPDATE vector_store
SET metadata = metadata::jsonb || 
    jsonb_build_object('tenant_id', tenant_id::text)
WHERE metadata IS NOT NULL;
