-- Migration 003: Add google_places source + google_place_id column
-- 2026-07-15 — M2.1 enrichment

-- 1. Add column (nullable for backward compat)
ALTER TABLE restaurants
ADD COLUMN IF NOT EXISTS google_place_id TEXT,
ADD COLUMN IF NOT EXISTS province_id TEXT DEFAULT '',
ADD COLUMN IF NOT EXISTS district_id TEXT;

-- 2. Index for faster lookup
CREATE INDEX IF NOT EXISTS idx_restaurants_google_place_id
  ON restaurants(google_place_id)
  WHERE google_place_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_restaurants_source
  ON restaurants(source);

-- 3. Verify
SELECT
  column_name,
  data_type,
  is_nullable
FROM information_schema.columns
WHERE table_name = 'restaurants'
  AND column_name IN ('google_place_id', 'province_id', 'district_id', 'source')
ORDER BY column_name;
