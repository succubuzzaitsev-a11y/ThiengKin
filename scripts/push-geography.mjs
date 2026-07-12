#!/usr/bin/env node
/**
 * Push Thailand geography data to Supabase PostgREST
 *
 * Source: data/thailand-geography.json (built by build-geography.mjs)
 * Target: Supabase project (URL + service_role key from .env)
 *
 * Tables populated:
 *   - regions     (6 records)
 *   - provinces   (77 records)
 *   - districts   (928 records)
 *
 * Restaurant table is NOT touched here — M3 OSM pipeline will populate it.
 *
 * Auth: service_role key (bypasses RLS — needed for INSERT)
 *
 * Idempotent: uses upsert (on conflict) — re-runs are safe
 *
 * Run: node scripts/push-geography.mjs
 * Env: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY in .env
 */
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const GEOJSON = join(ROOT, 'data', 'thailand-geography.json');
// Env file priority: thiengKin.env (project-specific) → .env (generic fallback)
const ENV_FILE = existsSync(join(ROOT, 'thiengKin.env'))
    ? join(ROOT, 'thiengKin.env')
    : join(ROOT, '.env');

/**
 * Minimal .env loader (no dotenv dep — one-line KEY=VALUE file)
 * Skips comment lines (#) and blank lines.
 */
function loadEnv() {
    if (!existsSync(ENV_FILE)) {
        throw new Error(`Missing env file.\nCreate one of:\n  D:\\thiengKin\\thiengKin.env  (recommended, project-named)\n  D:\\thiengKin\\.env          (generic fallback)\nWith content:\n  SUPABASE_URL=https://xxx.supabase.co\n  SUPABASE_SERVICE_ROLE_KEY=eyJ...`);
    }
    const env = {};
    for (const line of readFileSync(ENV_FILE, 'utf8').split(/\r?\n/)) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) continue;
        const m = trimmed.match(/^([A-Z_][A-Z0-9_]*)=(.*)$/);
        if (m) env[m[1]] = m[2].trim().replace(/^["']|["']$/g, '');
    }
    return env;
}

/**
 * Transform GeoJSON record to PostgREST row (flat + snake_case)
 * - camelCase → snake_case for all top-level keys
 * - bbox: {s,w,n,e} → bbox_s, bbox_w, bbox_n, bbox_e
 * - centroid: {lat,lng} → centroid_lat, centroid_lng
 */
function camelToSnake(s) {
    return s.replace(/([A-Z])/g, '_$1').toLowerCase();
}
function flattenRow(r) {
    const flat = {};
    for (const [k, v] of Object.entries(r)) {
        const snake = camelToSnake(k);
        if (k === 'bbox' && v && typeof v === 'object') {
            flat.bbox_s = v.s;
            flat.bbox_w = v.w;
            flat.bbox_n = v.n;
            flat.bbox_e = v.e;
        } else if (k === 'centroid' && v && typeof v === 'object') {
            flat.centroid_lat = v.lat;
            flat.centroid_lng = v.lng;
        } else {
            flat[snake] = v;
        }
    }
    return flat;
}

/**
 * PostgREST upsert via fetch.
 * Uses Prefer: resolution=merge-duplicates for upsert semantics.
 * Chunks to avoid request body size limits (PostgREST default ~10MB).
 */
async function upsertBatch(baseUrl, serviceKey, table, rows, chunkSize = 200) {
    let inserted = 0;
    let errors = 0;
    for (let i = 0; i < rows.length; i += chunkSize) {
        const chunk = rows.slice(i, i + chunkSize);
        const url = `${baseUrl}/rest/v1/${table}?on_conflict=id`;
        const res = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'apikey': serviceKey,
                'Authorization': `Bearer ${serviceKey}`,
                'Prefer': 'resolution=merge-duplicates,return=minimal',
            },
            body: JSON.stringify(chunk),
        });
        if (!res.ok) {
            const errText = await res.text();
            console.error(`  ✗ ${table} chunk ${i / chunkSize + 1} failed: HTTP ${res.status}`);
            console.error(`    ${errText.slice(0, 300)}`);
            errors += chunk.length;
        } else {
            inserted += chunk.length;
            console.log(`  ✓ ${table} chunk ${i / chunkSize + 1}: ${chunk.length} rows`);
        }
    }
    return { inserted, errors };
}

/**
 * Verify row count via PostgREST (read with count header)
 */
async function countRows(baseUrl, serviceKey, table) {
    const url = `${baseUrl}/rest/v1/${table}?select=id&limit=0`;
    const res = await fetch(url, {
        headers: {
            'apikey': serviceKey,
            'Authorization': `Bearer ${serviceKey}`,
            'Prefer': 'count=exact',
        },
    });
    if (!res.ok) return null;
    const countHeader = res.headers.get('content-range'); // e.g. "0-0/7"
    if (!countHeader) return null;
    const m = countHeader.match(/\/(\d+)/);
    return m ? Number(m[1]) : null;
}

// === Run ===
console.log('[push-geography] Loading env + data...');
const env = loadEnv();
const baseUrl = env.SUPABASE_URL.replace(/\/$/, '');
const serviceKey = env.SUPABASE_SERVICE_ROLE_KEY;

if (!baseUrl || !serviceKey) {
    console.error('Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY in .env');
    process.exit(1);
}

if (!existsSync(GEOJSON)) {
    console.error(`Missing ${GEOJSON} — run: node scripts/build-geography.mjs first`);
    process.exit(1);
}

const data = JSON.parse(readFileSync(GEOJSON, 'utf8'));
console.log(`  Source: regions=${data.regions.length} provinces=${data.provinces.length} districts=${data.districts.length}`);

const t0 = Date.now();
let totalInserted = 0;
let totalErrors = 0;

for (const [table, records] of [
    ['regions', data.regions],
    ['provinces', data.provinces],
    ['districts', data.districts],
]) {
    const rows = records.map(flattenRow);
    console.log(`\n[push-geography] Upserting ${rows.length} rows into "${table}"...`);
    const { inserted, errors } = await upsertBatch(baseUrl, serviceKey, table, rows);
    totalInserted += inserted;
    totalErrors += errors;
}

// === Verify ===
console.log('\n[push-geography] Verifying row counts...');
for (const table of ['regions', 'provinces', 'districts']) {
    const count = await countRows(baseUrl, serviceKey, table);
    console.log(`  ${table}: ${count} rows in DB`);
}

console.log(`\n[push-geography] Done in ${Date.now() - t0}ms — inserted=${totalInserted} errors=${totalErrors}`);

if (totalErrors > 0) {
    process.exit(1);
}
