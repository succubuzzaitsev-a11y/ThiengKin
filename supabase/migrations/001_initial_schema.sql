-- ============================================================================
-- Migration 001: Initial schema
-- ThiengKin — nationwide restaurant finder (Android)
-- Project: D:\thiengKin (M2 — Supabase backend)
-- ============================================================================
--
-- Tables:
--   - regions      : 6 NESDB regions (reference only — province_id FKs)
--   - provinces    : 77 provinces (2-digit official code, bbox + centroid)
--   - districts    : 928 districts (4-digit official code, province FK)
--   - restaurants  : populated by M3 OSM pipeline (schema only here)
--
-- Field naming: snake_case (PostgreSQL convention) — matches Android Room schema
-- Coordinate precision: DECIMAL(10,6) ≈ 11cm accuracy (good for ~100m grid)
-- Area precision: DECIMAL(12,4) supports up to 999,999.9999 km²
--
-- IMPORTANT: This is the **read-only public** schema.
--   - All tables: RLS enabled, public SELECT, no INSERT/UPDATE/DELETE for anon
--   - service_role key bypasses RLS (used by push script)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- regions (6 records — NESDB grouping)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS regions (
    id              TEXT PRIMARY KEY,                  -- "northern", "central"
    name_en         TEXT NOT NULL,
    name_th         TEXT,                              -- nullable — NESDB has no Thai names
    bbox_s          DECIMAL(10, 6) NOT NULL,           -- south lat
    bbox_w          DECIMAL(10, 6) NOT NULL,           -- west lng
    bbox_n          DECIMAL(10, 6) NOT NULL,           -- north lat
    bbox_e          DECIMAL(10, 6) NOT NULL,           -- east lng
    centroid_lat    DECIMAL(10, 6) NOT NULL,
    centroid_lng    DECIMAL(10, 6) NOT NULL,
    area_sqkm       DECIMAL(12, 4) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- provinces (77 records)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS provinces (
    id              TEXT PRIMARY KEY,                  -- "chiang_mai" (lowercase_underscore)
    code            TEXT NOT NULL UNIQUE,              -- "50" (2-digit official code)
    name_th         TEXT NOT NULL,
    name_en         TEXT NOT NULL,
    region_nesdb    TEXT REFERENCES regions(id) ON DELETE SET NULL,
    region_royin    TEXT,                              -- not FK (different ID space)
    bbox_s          DECIMAL(10, 6) NOT NULL,
    bbox_w          DECIMAL(10, 6) NOT NULL,
    bbox_n          DECIMAL(10, 6) NOT NULL,
    bbox_e          DECIMAL(10, 6) NOT NULL,
    centroid_lat    DECIMAL(10, 6) NOT NULL,
    centroid_lng    DECIMAL(10, 6) NOT NULL,
    area_sqkm       DECIMAL(12, 4) NOT NULL,
    perimeter_km    DECIMAL(12, 4),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_provinces_code ON provinces(code);
CREATE INDEX IF NOT EXISTS idx_provinces_region_nesdb ON provinces(region_nesdb);

-- ----------------------------------------------------------------------------
-- districts (928 records)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS districts (
    id              TEXT PRIMARY KEY,                  -- "mueang_chiang_mai"
    code            TEXT NOT NULL UNIQUE,              -- "5001" (4-digit official code)
    name_th         TEXT NOT NULL,
    name_en         TEXT NOT NULL,
    province_id     TEXT NOT NULL REFERENCES provinces(id) ON DELETE CASCADE,
    region_nesdb    TEXT,
    region_royin    TEXT,
    bbox_s          DECIMAL(10, 6) NOT NULL,
    bbox_w          DECIMAL(10, 6) NOT NULL,
    bbox_n          DECIMAL(10, 6) NOT NULL,
    bbox_e          DECIMAL(10, 6) NOT NULL,
    centroid_lat    DECIMAL(10, 6) NOT NULL,
    centroid_lng    DECIMAL(10, 6) NOT NULL,
    area_sqkm       DECIMAL(12, 4) NOT NULL,
    perimeter_km    DECIMAL(12, 4),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_districts_province_id ON districts(province_id);
CREATE INDEX IF NOT EXISTS idx_districts_code ON districts(code);

-- ----------------------------------------------------------------------------
-- restaurants (M3 will populate from OSM Overpass API)
-- Schema only here — no seed data
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS restaurants (
    id                  TEXT PRIMARY KEY,              -- "osm_123456" | "fsq_abc" | "manual_bkk_001"
    name                TEXT NOT NULL,
    name_th             TEXT,
    category            TEXT,                          -- "ก๋วยเตี๋ยว" (display)
    category_slug       TEXT,                          -- "noodle" (mapped from OSM cuisine)
    lat                 DECIMAL(10, 6) NOT NULL,
    lng                 DECIMAL(10, 6) NOT NULL,
    address             TEXT,
    district            TEXT,                          -- display name (legacy)
    province            TEXT,                          -- display name (legacy)
    tel                 TEXT,
    website             TEXT,
    rating              DECIMAL(3, 2),                 -- 0.00–5.00
    review_count        INTEGER,
    price               INTEGER,                       -- 1–4 (Foursquare tier)
    tags                TEXT[] NOT NULL DEFAULT '{}',  -- ["local_favorite", "morning", "noodle"]
    source              TEXT NOT NULL,                 -- "manual" | "osm" | "foursquare"
    is_favorite         BOOLEAN NOT NULL DEFAULT false,
    photo_url           TEXT,
    menu_text           TEXT,                          -- เมนูเด่น
    ai_summary          TEXT,                          -- AI review summary (Phase 2)
    province_id         TEXT NOT NULL DEFAULT '' REFERENCES provinces(id) ON DELETE CASCADE,
    district_id         TEXT REFERENCES districts(id) ON DELETE SET NULL,
    opening_hours       TEXT,                          -- OSM format
    capacity            INTEGER,
    source_updated_at   BIGINT,                        -- millis — cache age check
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_restaurants_province_id ON restaurants(province_id);
CREATE INDEX IF NOT EXISTS idx_restaurants_district_id ON restaurants(district_id);
CREATE INDEX IF NOT EXISTS idx_restaurants_source ON restaurants(source);
CREATE INDEX IF NOT EXISTS idx_restaurants_source_updated_at ON restaurants(source_updated_at);
CREATE INDEX IF NOT EXISTS idx_restaurants_lat_lng ON restaurants(lat, lng);

-- ============================================================================
-- End of migration 001
-- ============================================================================
