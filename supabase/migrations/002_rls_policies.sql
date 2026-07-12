-- ============================================================================
-- Migration 002: Row Level Security — read-only public
-- ThiengKin — M2
-- ============================================================================
--
-- Strategy: All geographic + restaurant data is **public reference data**
-- (OpenStreetMap-derived + manual curation). No auth required to read.
--
-- Writes only via service_role key (bypasses RLS) — used by:
--   - scripts/push-geography.mjs  (initial population)
--   - scripts/push-osm.mjs        (M3 OSM fetch)
--   - scripts/push-fsq.mjs        (optional FSQ enrichment)
--
-- Anon (public) access: SELECT only
-- Authenticated: SELECT only (no favorites/auth in Phase 1; M6 adds auth)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Enable RLS on all tables
-- ----------------------------------------------------------------------------
ALTER TABLE regions     ENABLE ROW LEVEL SECURITY;
ALTER TABLE provinces   ENABLE ROW LEVEL SECURITY;
ALTER TABLE districts   ENABLE ROW LEVEL SECURITY;
ALTER TABLE restaurants ENABLE ROW LEVEL SECURITY;

-- ----------------------------------------------------------------------------
-- Public read policies
-- ----------------------------------------------------------------------------

-- regions: public read
DROP POLICY IF EXISTS "Public read regions" ON regions;
CREATE POLICY "Public read regions" ON regions
    FOR SELECT
    TO anon, authenticated
    USING (true);

-- provinces: public read
DROP POLICY IF EXISTS "Public read provinces" ON provinces;
CREATE POLICY "Public read provinces" ON provinces
    FOR SELECT
    TO anon, authenticated
    USING (true);

-- districts: public read
DROP POLICY IF EXISTS "Public read districts" ON districts;
CREATE POLICY "Public read districts" ON districts
    FOR SELECT
    TO anon, authenticated
    USING (true);

-- restaurants: public read
DROP POLICY IF EXISTS "Public read restaurants" ON restaurants;
CREATE POLICY "Public read restaurants" ON restaurants
    FOR SELECT
    TO anon, authenticated
    USING (true);

-- ----------------------------------------------------------------------------
-- Service role bypasses RLS automatically — no explicit policy needed
-- (used by push scripts + future edge functions)
-- ----------------------------------------------------------------------------

-- ============================================================================
-- End of migration 002
-- ============================================================================
