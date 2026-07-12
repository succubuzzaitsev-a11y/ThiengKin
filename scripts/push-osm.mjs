#!/usr/bin/env node
/**
 * Push parsed OSM restaurants to Supabase PostgREST
 *
 * Source: data/parsed/osm-<name>.restaurants.json (built by osm-parse.mjs, M3.b)
 * Target: Supabase project — `restaurants` table
 *
 * **MIRROR** ของ RestaurantRepository.refreshArea() ใน Kotlin:
 *   1. Cache check: query DB for MAX(source_updated_at)
 *      WHERE province_id=X AND district_id=Y AND source='osm'
 *      - ถ้า fresh (age < DEFAULT_CACHE_TTL_MS = 7 days) และไม่ใช่ --force → skip
 *   2. Refresh: upsert all rows from file (`on_conflict=id`)
 *      - With --force: pre-delete by (province, district, source) for hard refresh
 *
 * **Upsert (not delete+insert) เพราะ**: script ประมวลผลหลายไฟล์ต่อจังหวัด (province-level + city-level)
 * ที่ OSM IDs overlap. Delete filter `WHERE district_id=chiang_mai_city` ไม่ match เพราะ
 * synthetic districtId ถูก nullify ตอน insert (FK safety) → กลายเป็น `district_id=NULL` ใน DB.
 * Upsert by id ปลอดภัยกว่า — idempotent, handles overlap.
 *
 * Kotlin `refreshArea()` ใช้ delete+insert ได้เพราะถูกเรียกครั้งละ 1 area (ไม่มี overlap).
 *
 * **FK safety**: district_id ในไฟล์อาจเป็น synthetic (เช่น `chiang_mai_city` ที่ parse.mjs
 * ตั้งให้ตอน city-level fetch) — script pre-fetch valid district IDs แล้ว nullify ที่ไม่ valid
 * (เพื่อไม่ให้ FK reject ทั้ง batch)
 *
 * **Schema mapping**: osm-parse.mjs output เป็น camelCase (ตาม Restaurant.kt)
 * → PostgREST ต้องการ snake_case (ตาม 001_initial_schema.sql)
 *
 * Auth: service_role key (bypasses RLS)
 *
 * Idempotent: re-run ภายใน TTL → skip (no writes). นอก TTL → upsert.
 *
 * Run:   node scripts/push-osm.mjs <name> [--force] [--dry-run]
 *        node scripts/push-osm.mjs --all [--force] [--dry-run]
 *        node scripts/push-osm.mjs --list
 * Env:   SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY in thiengKin.env
 */
import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { join, dirname, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const PARSED_DIR = join(ROOT, 'data', 'parsed');
// Env file priority: thiengKin.env (project-specific) → .env (generic fallback)
const ENV_FILE = existsSync(join(ROOT, 'thiengKin.env'))
    ? join(ROOT, 'thiengKin.env')
    : join(ROOT, '.env');

/** Cache TTL — must match RestaurantRepository.DEFAULT_CACHE_TTL_MS (7 days) */
const DEFAULT_CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000;

/** PostgREST chunk size (rows per POST) — keep under 10MB body limit */
const CHUNK_SIZE = 200;

// === Env loader (same as push-geography.mjs) ===

function loadEnv() {
    if (!existsSync(ENV_FILE)) {
        throw new Error(`Missing env file. Create ${ENV_FILE} with SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY`);
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

// === Schema transform (camelCase → snake_case, no nested objects in restaurant) ===

function camelToSnake(s) {
    return s.replace(/([A-Z])/g, '_$1').toLowerCase();
}

/**
 * Convert one Restaurant object → PostgREST row.
 * - Top-level camelCase keys → snake_case
 * - All schema columns are flat (no nested bbox/centroid like geography)
 */
function toDbRow(r) {
    const row = {};
    for (const [k, v] of Object.entries(r)) {
        row[camelToSnake(k)] = v;
    }
    return row;
}

// === Supabase REST helpers ===

async function supabaseFetch(baseUrl, key, path, init = {}) {
    const url = `${baseUrl}/rest/v1/${path}`;
    return fetch(url, {
        ...init,
        headers: {
            'apikey': key,
            'Authorization': `Bearer ${key}`,
            ...(init.headers || {}),
        },
    });
}

/**
 * Query latest source_updated_at for a (provinceId, districtId?, source) — returns ms or null
 *
 * Cache key granularity: (province_id, district_id, source)
 * - province-level fetch (districtId=null) → WHERE district_id IS NULL
 * - city-level fetch (districtId=chiang_mai_city synthetic) → WHERE district_id IS NULL
 *   (script nullifies synthetic districtIds before insert)
 * - real district fetch (districtId=nonthaburi_mueang_nonthaburi) → WHERE district_id = X
 *
 * This prevents city/province files of the same province from "stealing" each other's cache.
 */
async function latestUpdateByProvinceAndSource(baseUrl, key, provinceId, districtId, source) {
    const districtFilter = districtId == null
        ? '&district_id=is.null'
        : `&district_id=eq.${encodeURIComponent(districtId)}`;
    const path = `restaurants?select=source_updated_at&province_id=eq.${encodeURIComponent(provinceId)}&source=eq.${encodeURIComponent(source)}${districtFilter}&order=source_updated_at.desc&limit=1`;
    const res = await supabaseFetch(baseUrl, key, path, {
        headers: { 'Accept': 'application/json' },
    });
    if (!res.ok) {
        const errText = await res.text();
        throw new Error(`latestUpdateByProvinceAndSource failed: HTTP ${res.status} — ${errText.slice(0, 200)}`);
    }
    const rows = await res.json();
    if (!rows || rows.length === 0) return null;
    return Number(rows[0].source_updated_at);
}

/** Pre-fetch all valid district IDs (for FK validation) */
async function fetchValidDistrictIds(baseUrl, key) {
    const res = await supabaseFetch(baseUrl, key, 'districts?select=id', {
        headers: { 'Accept': 'application/json' },
    });
    if (!res.ok) {
        const errText = await res.text();
        throw new Error(`fetchValidDistrictIds failed: HTTP ${res.status} — ${errText.slice(0, 200)}`);
    }
    const rows = await res.json();
    return new Set(rows.map(r => r.id));
}

/** Pre-fetch all valid province IDs (for FK validation on province_id) */
async function fetchValidProvinceIds(baseUrl, key) {
    const res = await supabaseFetch(baseUrl, key, 'provinces?select=id', {
        headers: { 'Accept': 'application/json' },
    });
    if (!res.ok) {
        const errText = await res.text();
        throw new Error(`fetchValidProvinceIds failed: HTTP ${res.status} — ${errText.slice(0, 200)}`);
    }
    const rows = await res.json();
    return new Set(rows.map(r => r.id));
}

/**
 * Delete all rows WHERE province_id=X AND (district_id=Y OR district_id IS NULL) AND source=Z
 *
 * Mirrors Kotlin `deleteByProvinceAndSource(provinceId, source)` BUT scoped by district
 * to avoid wiping unrelated city/district fetches under the same province.
 * - districtId=null → delete only province-level rows (district_id IS NULL)
 * - districtId=X → delete rows with district_id=X
 */
async function deleteByProvinceAndSource(baseUrl, key, provinceId, districtId, source) {
    const districtFilter = districtId == null
        ? `&district_id=is.null`
        : `&district_id=eq.${encodeURIComponent(districtId)}`;
    const path = `restaurants?province_id=eq.${encodeURIComponent(provinceId)}&source=eq.${encodeURIComponent(source)}${districtFilter}`;
    const res = await supabaseFetch(baseUrl, key, path, {
        method: 'DELETE',
        headers: { 'Prefer': 'return=minimal' },
    });
    if (!res.ok) {
        const errText = await res.text();
        throw new Error(`deleteByProvinceAndSource failed: HTTP ${res.status} — ${errText.slice(0, 200)}`);
    }
    return true;
}

/**
 * Bulk upsert in chunks (POST + Prefer: resolution=merge-duplicates)
 *
 * Strategy: upsert by `id` (primary key) — handles overlapping data across
 * province-level + city-level fetches under the same province. Idempotent.
 *
 * Why not delete+insert (mirror Kotlin exactly):
 * - Kotlin `refreshArea()` is called once per area with one bbox — no overlap
 * - Script processes multiple files for the same province (province-level + city-level)
 *   whose OSM IDs can overlap
 * - Synthetic city-level districtId (e.g. `chiang_mai_city`) gets nullified by FK
 *   safety → delete filter `WHERE district_id=chiang_mai_city` no longer matches
 * - Upsert is the only safe behavior for overlapping data
 *
 * For hard refresh of stale data, use `--force` which adds a pre-delete step.
 */
async function upsertBatch(baseUrl, key, rows, { label = 'upsert' } = {}) {
    let inserted = 0;
    let errors = 0;
    const totalChunks = Math.ceil(rows.length / CHUNK_SIZE);
    for (let i = 0; i < rows.length; i += CHUNK_SIZE) {
        const chunk = rows.slice(i, i + CHUNK_SIZE);
        const res = await supabaseFetch(baseUrl, key, 'restaurants?on_conflict=id', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Prefer': 'resolution=merge-duplicates,return=minimal',
            },
            body: JSON.stringify(chunk),
        });
        if (!res.ok) {
            const errText = await res.text();
            console.error(`    ✗ ${label} chunk ${i / CHUNK_SIZE + 1}/${totalChunks} failed: HTTP ${res.status}`);
            console.error(`      ${errText.slice(0, 400)}`);
            errors += chunk.length;
        } else {
            inserted += chunk.length;
            if (totalChunks > 1) {
                console.log(`    ✓ ${label} chunk ${i / CHUNK_SIZE + 1}/${totalChunks}: ${chunk.length} rows`);
            }
        }
    }
    return { inserted, errors };
}

/** Count rows in DB matching filter — for verification */
async function countRows(baseUrl, key, table, filter = '') {
    const path = filter
        ? `${table}?select=id&${filter}&limit=0`
        : `${table}?select=id&limit=0`;
    const res = await supabaseFetch(baseUrl, key, path, {
        headers: { 'Prefer': 'count=exact' },
    });
    if (!res.ok) return null;
    const countHeader = res.headers.get('content-range'); // e.g. "0-0/7"
    if (!countHeader) return null;
    const m = countHeader.match(/\/(\d+)/);
    return m ? Number(m[1]) : null;
}

// === Per-file push logic ===

/**
 * Process one parsed file. Returns stats object.
 *
 * @param {object} opts
 * @param {string} opts.name — file stem (e.g. "chiang-mai-city")
 * @param {string} opts.baseUrl
 * @param {string} opts.key — service_role key
 * @param {Set<string>} opts.validDistricts
 * @param {Set<string>} opts.validProvinces
 * @param {boolean} [opts.force=false] — bypass cache TTL
 * @param {boolean} [opts.dryRun=false] — show what would happen, no writes
 * @param {number} [opts.cacheTtlMs=7 days]
 */
async function pushOne(opts) {
    const { name, baseUrl, key, validDistricts, validProvinces, force, dryRun, cacheTtlMs = DEFAULT_CACHE_TTL_MS } = opts;

    const restaurantsFile = join(PARSED_DIR, `osm-${name}.restaurants.json`);
    const metaFile = join(PARSED_DIR, `osm-${name}.restaurants.meta.json`);

    if (!existsSync(restaurantsFile)) {
        console.error(`  ✗ Not found: ${relative(ROOT, restaurantsFile)}`);
        console.error(`    Run: npm run parse:osm ${name} -- --province <id>`);
        return { name, status: 'missing-file' };
    }
    if (!existsSync(metaFile)) {
        console.error(`  ✗ Not found: ${relative(ROOT, metaFile)}`);
        return { name, status: 'missing-meta' };
    }

    const meta = JSON.parse(readFileSync(metaFile, 'utf8'));
    const provinceId = meta.provinceId;
    const districtId = meta.districtId ?? null;
    const fileCount = meta.parsed ?? 0;

    if (!provinceId) {
        console.error(`  ✗ meta.provinceId is empty in ${relative(ROOT, metaFile)}`);
        return { name, status: 'missing-province' };
    }
    if (!validProvinces.has(provinceId)) {
        console.error(`  ✗ province_id='${provinceId}' not in provinces table (FK would fail)`);
        console.error(`    Hint: run \`npm run push:geo\` first to populate provinces`);
        return { name, status: 'invalid-province' };
    }

    console.log(`\n📂 ${name}`);
    console.log(`   provinceId=${provinceId}  districtId=${districtId ?? '(none)'}  rows=${fileCount}`);

    // === 1. Cache check (mirror Kotlin refreshArea) ===
    const nowMs = Date.now();
    // Synthetic districtId (not in districts table) gets nullified on insert → cache key in DB
    // would be `district_id=NULL` (same as province-level rows). To avoid cache ambiguity, skip
    // the cache check entirely for synthetic districtIds — upsert is idempotent so this is safe.
    const isSyntheticDistrict = districtId != null && !validDistricts.has(districtId);

    let dbLatest = null, ageMs = null, cacheExpired = true;
    if (isSyntheticDistrict) {
        console.log(`   ⚠️  Synthetic districtId='${districtId}' (not in districts table) — cache check skipped (upsert is idempotent)`);
    } else {
        dbLatest = await latestUpdateByProvinceAndSource(baseUrl, key, provinceId, districtId, 'osm');
        ageMs = dbLatest != null ? nowMs - dbLatest : null;
        cacheExpired = dbLatest == null || ageMs > cacheTtlMs;
    }

    if (!force && !cacheExpired) {
        const ageHours = (ageMs / (60 * 60 * 1000)).toFixed(1);
        const ttlHours = (cacheTtlMs / (60 * 60 * 1000)).toFixed(0);
        console.log(`   ⏭️  SKIP — cache fresh (age=${ageHours}h, ttl=${ttlHours}h, scope=province:${provinceId}/district:${districtId ?? '(null)'})`);
        console.log(`      Use --force to bypass.`);
        return { name, status: 'skipped', reason: 'cache-fresh', dbLatest, provinceId, districtId };
    }

    if (force) console.log(`   🔥 --force: bypass cache check`);
    else if (isSyntheticDistrict) console.log(`   🆕 Synthetic districtId — proceeding with upsert (no cache check)`);
    else if (dbLatest == null) console.log(`   🆕 No existing rows for (province=${provinceId}, district=${districtId ?? '(null)'}) — full insert`);
    else console.log(`   ♻️  Cache expired (age=${(ageMs / (60 * 60 * 1000)).toFixed(1)}h > ${(cacheTtlMs / (60 * 60 * 1000)).toFixed(0)}h) — refresh`);

    // === 2. Load + transform rows ===
    const restaurants = JSON.parse(readFileSync(restaurantsFile, 'utf8'));
    if (restaurants.length === 0) {
        console.log(`   ⚠️  File has 0 restaurants — nothing to push`);
        return { name, status: 'empty-file', provinceId, districtId };
    }

    // FK validation: nullify district_id if not in districts table (synthetic like "chiang_mai_city")
    let syntheticDistrictCount = 0;
    for (const r of restaurants) {
        if (r.districtId && !validDistricts.has(r.districtId)) {
            r.districtId = null;
            syntheticDistrictCount++;
        }
    }
    if (syntheticDistrictCount > 0 && syntheticDistrictCount !== restaurants.length) {
        console.log(`   ⚠️  Nullified ${syntheticDistrictCount} synthetic districtId(s) (FK safety)`);
    } else if (syntheticDistrictCount === restaurants.length) {
        console.log(`   ⚠️  All ${syntheticDistrictCount} rows had synthetic districtId → district_id set to NULL (FK safety)`);
    }

    // Transform camelCase → snake_case
    const rows = restaurants.map(toDbRow);
    console.log(`   📦 ${rows.length} rows ready to push`);

    if (dryRun) {
        const isSynthetic = districtId != null && !validDistricts.has(districtId);
        const deleteNote = (force && !isSynthetic)
            ? `delete WHERE province_id='${provinceId}' AND district_id=${districtId ?? '(null)'} AND source='osm' + `
            : '';
        console.log(`   🧪 --dry-run: would ${deleteNote}upsert ${rows.length} rows (on_conflict=id)`);
        console.log(`      Sample row: ${JSON.stringify(rows[0]).slice(0, 200)}...`);
        return { name, status: 'dry-run', rows: rows.length, provinceId, districtId };
    }

    // === 3. --force: pre-delete by scope (removes stale data before upsert) ===
    if (force) {
        const isSynthetic = districtId != null && !validDistricts.has(districtId);
        if (isSynthetic) {
            console.log(`   ⚠️  --force: synthetic district_id='${districtId}' → delete filter would be ineffective (FK nullifies on insert)`);
            console.log(`      Skipping pre-delete — will only upsert. Use --force after fixing district_id to a real FK.`);
        } else {
            console.log(`   🗑️  --force: deleting rows for province_id='${provinceId}' district_id=${districtId ?? '(null)'} source='osm'...`);
            await deleteByProvinceAndSource(baseUrl, key, provinceId, districtId, 'osm');
        }
    }

    // === 4. Upsert (idempotent, handles overlapping data) ===
    console.log(`   📥 Upserting ${rows.length} rows (on_conflict=id)...`);
    const t0 = Date.now();
    const { inserted, errors } = await upsertBatch(baseUrl, key, rows, { label: 'upsert' });
    const elapsed = ((Date.now() - t0) / 1000).toFixed(1);

    if (errors > 0) {
        console.log(`   ❌ ${inserted} upserted, ${errors} errors in ${elapsed}s`);
        return { name, status: 'partial', inserted, errors, provinceId, districtId };
    }
    console.log(`   ✅ ${inserted} upserted in ${elapsed}s`);

    return { name, status: 'pushed', inserted, errors: 0, provinceId, districtId };
}

// === CLI ===

function parseArgs() {
    const args = process.argv.slice(2);
    if (args.includes('--help') || args.includes('-h')) return { help: true };
    if (args.includes('--list')) return { list: true };

    // Parse flags first (--all, --force, --dry-run can combine with each other)
    const all = args.includes('--all');
    const force = args.includes('--force');
    const dryRun = args.includes('--dry-run');

    // First non-flag arg is the name (if no --all)
    const positional = args.filter(a => !a.startsWith('--'));
    const name = all ? null : (positional[0] || null);

    return { name, all, force, dryRun };
}

function printHelp() {
    console.log(`🌍 ThiengKin · OSM Push (M3.c)

Push parsed OSM restaurants (data/parsed/osm-*.restaurants.json) → Supabase.
**MIRROR** ของ RestaurantRepository.refreshArea() — cache check 7 days, delete+insert.

Usage:
  node scripts/push-osm.mjs <name>            # push one, with cache check
  node scripts/push-osm.mjs <name> --force    # bypass cache TTL
  node scripts/push-osm.mjs <name> --dry-run  # show what would happen, no writes
  node scripts/push-osm.mjs --all             # push all parsed files
  node scripts/push-osm.mjs --list            # list available parsed files

Schema: matches 001_initial_schema.sql → restaurants table (snake_case).
Auth:  service_role key (bypasses RLS).

Examples:
  node scripts/push-osm.mjs mueang-nonthaburi              # first push (no cache)
  node scripts/push-osm.mjs chiang-mai --force             # full refresh
  node scripts/push-osm.mjs --all --dry-run                # preview all
`);
}

function listFiles() {
    if (!existsSync(PARSED_DIR)) { console.log('❌ No data/parsed/ directory — run osm-parse first'); return; }
    const files = listParsedFiles();
    if (files.length === 0) { console.log('❌ No data/parsed/osm-*.restaurants.json — run npm run parse:osm first'); return; }
    console.log('Available parsed files:');
    for (const f of files) {
        const kb = (f.size / 1024).toFixed(1);
        const province = f.provinceId ? `→ ${f.provinceId}` : '(no provinceId)';
        console.log(`  osm-${f.name}.restaurants.json  ${String(f.parsed).padStart(5)} rows  ${kb.padStart(8)} KB  ${province}`);
    }
}

function listParsedFiles() {
    if (!existsSync(PARSED_DIR)) return [];
    return readdirSync(PARSED_DIR)
        .filter(f => /^osm-.*\.restaurants\.json$/.test(f) && !f.endsWith('.meta.json'))
        .map(f => {
            const metaPath = join(PARSED_DIR, f.replace(/\.restaurants\.json$/, '.restaurants.meta.json'));
            let meta = {};
            try { meta = JSON.parse(readFileSync(metaPath, 'utf8')); } catch { /* ignore */ }
            return {
                name: f.replace(/^osm-/, '').replace(/\.restaurants\.json$/, ''),
                size: statSync(join(PARSED_DIR, f)).size,
                parsed: meta.parsed ?? 0,
                provinceId: meta.provinceId,
                districtId: meta.districtId,
            };
        })
        .sort((a, b) => a.name.localeCompare(b.name));
}

async function main() {
    const args = parseArgs();
    if (args.help) { printHelp(); return; }

    console.log('🌍 ThiengKin · OSM Push (M3.c)');
    console.log('━'.repeat(60));

    // === Load env ===
    let env;
    try { env = loadEnv(); } catch (e) { console.error(`\n💥 ${e.message}`); process.exit(1); }
    const baseUrl = env.SUPABASE_URL.replace(/\/$/, '');
    const serviceKey = env.SUPABASE_SERVICE_ROLE_KEY;
    if (!baseUrl || !serviceKey) {
        console.error('Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY in .env');
        process.exit(1);
    }
    console.log(`   Supabase: ${baseUrl}`);

    if (args.list) { listFiles(); return; }

    // === Pre-fetch FK reference data ===
    console.log('\n🔍 Loading FK reference data...');
    const t0 = Date.now();
    const [validDistricts, validProvinces] = await Promise.all([
        fetchValidDistrictIds(baseUrl, serviceKey),
        fetchValidProvinceIds(baseUrl, serviceKey),
    ]);
    console.log(`   provinces=${validProvinces.size}  districts=${validDistricts.size}  (${Date.now() - t0}ms)`);

    // === Collect targets ===
    let targets = [];
    if (args.all) {
        const all = listParsedFiles();
        if (all.length === 0) { console.log('\n❌ No parsed files to push'); return; }
        targets = all.map(f => f.name);
        console.log(`\n🎯 Targets (--all): ${targets.join(', ')}`);
    } else if (args.name) {
        targets = [args.name];
    } else {
        console.error('\n❌ Provide a name or --all / --list / --help');
        process.exit(1);
    }

    if (args.dryRun) console.log(`\n🧪 DRY-RUN mode — no writes will be made`);
    if (args.force) console.log(`🔥 --force: cache check bypassed`);

    // === Push loop ===
    const t0push = Date.now();
    const results = [];
    for (const name of targets) {
        try {
            const r = await pushOne({
                name, baseUrl, key: serviceKey,
                validDistricts, validProvinces,
                force: args.force, dryRun: args.dryRun,
            });
            results.push(r);
        } catch (e) {
            console.error(`\n💥 ${name}: ${e.message}`);
            if (e.stack) console.error(e.stack);
            results.push({ name, status: 'error', error: e.message });
        }
    }

    // === Summary ===
    const elapsedTotal = ((Date.now() - t0push) / 1000).toFixed(1);
    console.log('\n' + '━'.repeat(60));
    console.log(`📊 Summary (${elapsedTotal}s):`);
    const statusCounts = {};
    let totalInserted = 0;
    for (const r of results) {
        statusCounts[r.status] = (statusCounts[r.status] || 0) + 1;
        if (r.inserted) totalInserted += r.inserted;
    }
    for (const [status, count] of Object.entries(statusCounts)) {
        console.log(`   ${status.padEnd(20)} ${count}`);
    }
    if (totalInserted > 0) console.log(`   ${'total rows inserted'.padEnd(20)} ${totalInserted}`);

    // === Verify (only if not dry-run) ===
    if (!args.dryRun) {
        console.log('\n🔍 Verifying DB row counts...');
        for (const r of results) {
            if (r.status !== 'pushed' && r.status !== 'partial' && r.status !== 'skipped') continue;
            const provinceId = r.provinceId;
            if (!provinceId) continue;
            // Query by (province, district) scope to match the cache key
            const districtFilter = r.districtId == null
                ? '&district_id=is.null'
                : `&district_id=eq.${encodeURIComponent(r.districtId)}`;
            const count = await countRows(
                baseUrl, serviceKey, 'restaurants',
                `source=eq.osm&province_id=eq.${encodeURIComponent(provinceId)}${districtFilter}`,
            );
            const scope = r.districtId ? `${provinceId}/${r.districtId}` : `${provinceId}/(province-level)`;
            console.log(`   ${scope.padEnd(50)} osm rows in DB: ${count ?? '(error)'}`);
        }
        // Also show total osm row count
        const total = await countRows(baseUrl, serviceKey, 'restaurants', 'source=eq.osm');
        console.log(`   ${'TOTAL osm rows in restaurants table'.padEnd(50)} ${total ?? '(error)'}`);
    }
}

main().catch(err => {
    console.error('\n💥 Fatal:', err.message);
    if (err.stack) console.error(err.stack);
    process.exit(1);
});
