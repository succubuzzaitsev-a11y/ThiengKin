// sweep-osm.mjs
// เที่ยงกิน · M3.d — Full 77-province OSM sweep
//
// Orchestrate 3 phases: fetch (77 Overpass calls) → parse (--all) → push (--all --force)
//
// Usage:
//   node scripts/sweep-osm.mjs                # full sweep (skip already-fetched)
//   node scripts/sweep-osm.mjs --force-fetch  # re-fetch even if file exists
//   node scripts/sweep-osm.mjs --skip-fetch   # skip phase 1 (use existing data/osm-*.json)
//   node scripts/sweep-osm.mjs --skip-push    # fetch+parse only (no Supabase writes)
//   node scripts/sweep-osm.mjs --dry-run      # plan only, no writes, no network
//   node scripts/sweep-osm.mjs --only <phase> # fetch | parse | push | verify
//
// Output:
//   data/sweep-<timestamp>.log             — per-phase transcript
//   data/sweep-<timestamp>.summary.json    — final stats
//
// Resumable: phase 1 skips provinces whose data/osm-<id>.json already exists
//   (use --force-fetch to re-fetch anyway).
//
// Safety:
//   - Sequential fetch (Overpass public server rate-limits parallel)
//   - 1.5s politeness delay between Overpass calls
//   - 429/504 → wait 30s + retry × 3
//   - Per-province failures don't crash the sweep (logged, continue)
//   - Total Overpass usage: 77 calls = 0.77% of 10,000/day limit
//
// Estimated time:
//   - Phase 1: 77 × ~5s + 77 × 1.5s = ~8 min
//   - Phase 2: ~1 min
//   - Phase 3: ~7 min (network bound)
//   - Total: ~15-20 min

import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync, mkdirSync, statSync, readdirSync } from 'node:fs';
import { join, dirname, relative, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = join(__dirname, '..');
const SCRIPTS_DIR = __dirname;
const DATA_DIR = join(ROOT, 'data');
const GEOGRAPHY_FILE = join(DATA_DIR, 'thailand-geography.json');

const OVERPASS_COOLDOWN_MS = 2500;  // politeness between calls
const OVERPASS_BATCH_SIZE = 5;  // every N provinces, take a longer break
const OVERPASS_BATCH_COOLDOWN_MS = 15000;  // 15s after every 5 provinces
const OVERPASS_MAX_RETRIES = 2;  // per-province (endpoint fallback handles rate limit)
const OVERPASS_RETRY_BASE_MS = 45000;  // 45s, 90s
const PHASE3_CHUNK_ESTIMATE_S = 5;  // per-file push estimate
const PHASE1_CHUNK_ESTIMATE_S = 5;  // per-province fetch estimate

// === Env loader (same as push-osm.mjs) ===
function loadEnv() {
    const envFile = existsSync(join(ROOT, 'thiengKin.env'))
        ? join(ROOT, 'thiengKin.env')
        : join(ROOT, '.env');
    if (!existsSync(envFile)) {
        throw new Error(`Missing env file: ${envFile}`);
    }
    const env = {};
    for (const line of readFileSync(envFile, 'utf8').split(/\r?\n/)) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) continue;
        const m = trimmed.match(/^([A-Z_][A-Z0-9_]*)=(.*)$/);
        if (m) env[m[1]] = m[2].trim().replace(/^["']|["']$/g, '');
    }
    return env;
}

// === Supabase helpers (for verify phase) ===
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

async function countRows(baseUrl, key, table, filter = '') {
    const path = filter
        ? `${table}?select=id&${filter}&limit=0`
        : `${table}?select=id&limit=0`;
    const res = await supabaseFetch(baseUrl, key, path, {
        headers: { 'Prefer': 'count=exact' },
    });
    if (!res.ok) return null;
    const countHeader = res.headers.get('content-range');
    if (!countHeader) return null;
    const m = countHeader.match(/\/(\d+)/);
    return m ? Number(m[1]) : null;
}

// === CLI ===
function parseArgs() {
    const args = process.argv.slice(2);
    const opts = {
        forceFetch: args.includes('--force-fetch'),
        skipFetch: args.includes('--skip-fetch'),
        skipPush: args.includes('--skip-push'),
        dryRun: args.includes('--dry-run'),
        only: null,
        startFrom: null,
        help: args.includes('--help') || args.includes('-h'),
    };
    const onlyIdx = args.indexOf('--only');
    if (onlyIdx >= 0 && args[onlyIdx + 1]) {
        const phase = args[onlyIdx + 1];
        if (!['fetch', 'parse', 'push', 'verify'].includes(phase)) {
            throw new Error(`Invalid --only phase: ${phase}. Must be: fetch | parse | push | verify`);
        }
        opts.only = phase;
    }
    const fromIdx = args.indexOf('--start-from');
    if (fromIdx >= 0 && args[fromIdx + 1]) {
        opts.startFrom = args[fromIdx + 1];  // province id like "chiang_mai" (resumes from that province onwards)
    }
    return opts;
}

function printHelp() {
    console.log(`🌍 ThiengKin · OSM Sweep (M3.d)

Full 77-province OSM sweep: fetch → parse → push.

Usage:
  node scripts/sweep-osm.mjs                       # full sweep (skip already-fetched)
  node scripts/sweep-osm.mjs --force-fetch         # re-fetch even if file exists
  node scripts/sweep-osm.mjs --skip-fetch          # skip phase 1
  node scripts/sweep-osm.mjs --skip-push           # fetch+parse only (no Supabase writes)
  node scripts/sweep-osm.mjs --dry-run             # plan only, no network, no writes
  node scripts/sweep-osm.mjs --only <phase>        # fetch | parse | push | verify
  node scripts/sweep-osm.mjs --start-from <id>     # resume from province id (alphabetical)

Phases:
  1. fetch  — 77 Overpass calls (sequential, resumable, rate-limit aware, 3-endpoint fallback)
  2. parse  — parse all data/osm-*.json → data/parsed/
  3. push   — push all data/parsed/*.restaurants.json → Supabase (with --force)
  4. verify — query Supabase for row counts by province

Resumable: phase 1 skips provinces whose data/osm-<id>.json already exists.
Use --start-from <id> to resume from a specific province (alphabetical order).
`);
}

// === Phase 1: Fetch ===
async function phase1Fetch({ log, forceFetch, dryRun, onlyThis, startFrom }) {
    if (onlyThis && onlyThis !== 'fetch') {
        log('⏭️  Skipping Phase 1 (--only ' + onlyThis + ')');
        return { phase: 'fetch', skipped: true };
    }

    log('\n' + '━'.repeat(60));
    log('🌍 Phase 1: Fetch 77 provinces from Overpass');
    log('━'.repeat(60));

    if (!existsSync(GEOGRAPHY_FILE)) {
        throw new Error(`Missing ${GEOGRAPHY_FILE}`);
    }
    const geo = JSON.parse(readFileSync(GEOGRAPHY_FILE, 'utf8'));
    const provinces = geo.provinces;
    log(`   Total provinces: ${provinces.length}`);

    if (dryRun) {
        log('   🧪 --dry-run: would fetch ' + provinces.length + ' provinces (no network)');
        return { phase: 'fetch', dryRun: true, total: provinces.length };
    }

    const opts = { startFrom };  // for --start-from filter

    const results = {
        fetched: 0,
        skipped: 0,
        failed: 0,
        errors: [],
        perProvince: [],
    };

    const t0 = Date.now();
    let consecutiveFetches = 0;
    for (let i = 0; i < provinces.length; i++) {
        const p = provinces[i];

        // --start-from filter
        if (opts?.startFrom && p.id.localeCompare(opts.startFrom) < 0) {
            continue;
        }

        const name = p.id.replace(/_/g, '-');
        const outFile = join(DATA_DIR, `osm-${name}.json`);
        const metaFile = join(DATA_DIR, `osm-${name}.meta.json`);
        const progress = `[${String(i + 1).padStart(2)}/${provinces.length}]`;

        // Resumable: skip if already fetched
        if (!forceFetch && existsSync(outFile) && existsSync(metaFile)) {
            const sizeKB = (statSync(outFile).size / 1024).toFixed(1);
            log(`   ${progress} ⏭️  ${p.id.padEnd(28)} (exists, ${sizeKB} KB)`);
            results.skipped++;
            results.perProvince.push({ id: p.id, status: 'skipped' });
            continue;
        }

        log(`   ${progress} 🌍 ${p.id.padEnd(28)} bbox=[${p.bbox.s.toFixed(3)}, ${p.bbox.w.toFixed(3)}, ${p.bbox.n.toFixed(3)}, ${p.bbox.e.toFixed(3)}]`);

        let success = false;
        let lastError = null;
        for (let attempt = 1; attempt <= OVERPASS_MAX_RETRIES; attempt++) {
            const tFetch = Date.now();
            const r = spawnSync('node', [join(SCRIPTS_DIR, 'osm-fetch.mjs'), p.id], {
                cwd: ROOT,
                encoding: 'utf8',
                timeout: 120000,  // 120s per attempt
            });
            const elapsed = ((Date.now() - tFetch) / 1000).toFixed(1);

            if (r.status === 0 && r.stdout && r.stdout.includes('Saved:')) {
                log(`      ✅ done in ${elapsed}s`);
                results.fetched++;
                results.perProvince.push({ id: p.id, status: 'fetched', elapsedSeconds: parseFloat(elapsed) });
                success = true;
                consecutiveFetches++;
                break;
            }

            // Detect 429/504 — endpoint fallback should handle these; we still retry on persistent issues
            const out = (r.stdout || '') + (r.stderr || '');
            const isRateLimit = /\b429\b|Too Many|504|Gateway|timeout|exceeded/i.test(out);
            lastError = (r.stderr || r.stdout || '').trim().split('\n').slice(-3).join(' | ').slice(0, 200);

            if (isRateLimit && attempt < OVERPASS_MAX_RETRIES) {
                const waitMs = OVERPASS_RETRY_BASE_MS * Math.pow(2, attempt - 1);
                log(`      ⚠️  All endpoints rate-limited (attempt ${attempt}/${OVERPASS_MAX_RETRIES}) — waiting ${waitMs / 1000}s...`);
                await sleep(waitMs);
            } else {
                log(`      ❌ failed (attempt ${attempt}/${OVERPASS_MAX_RETRIES}): ${lastError}`);
                if (attempt >= OVERPASS_MAX_RETRIES) break;
            }
        }

        if (!success) {
            results.failed++;
            results.errors.push({ id: p.id, error: lastError });
            results.perProvince.push({ id: p.id, status: 'failed', error: lastError });
            log(`      💥 giving up on ${p.id}`);
            consecutiveFetches = 0;
        }

        // Politeness delay
        if (i < provinces.length - 1) {
            await sleep(OVERPASS_COOLDOWN_MS);
            // Batch cooldown: after every N successful fetches, take a longer break
            if (consecutiveFetches > 0 && consecutiveFetches % OVERPASS_BATCH_SIZE === 0) {
                log(`      💤 batch cooldown (${consecutiveFetches} in a row) — ${OVERPASS_BATCH_COOLDOWN_MS / 1000}s...`);
                await sleep(OVERPASS_BATCH_COOLDOWN_MS);
            }
        }
    }

    const elapsedTotal = ((Date.now() - t0) / 1000).toFixed(1);
    log(`\n   📊 Phase 1 done in ${elapsedTotal}s: fetched=${results.fetched} skipped=${results.skipped} failed=${results.failed}`);

    return { phase: 'fetch', ...results, elapsedSeconds: parseFloat(elapsedTotal) };
}

// === Phase 2: Parse ===
async function phase2Parse({ log, dryRun, onlyThis }) {
    if (onlyThis && onlyThis !== 'parse') {
        log('⏭️  Skipping Phase 2 (--only ' + onlyThis + ')');
        return { phase: 'parse', skipped: true };
    }

    log('\n' + '━'.repeat(60));
    log('📂 Phase 2: Parse all data/osm-*.json → data/parsed/');
    log('━'.repeat(60));

    // Count input files
    const inputFiles = readdirSync(DATA_DIR)
        .filter(f => /^osm-.*\.json$/.test(f) && !f.endsWith('.meta.json'));
    log(`   Input files: ${inputFiles.length}`);

    if (dryRun) {
        log('   🧪 --dry-run: would run npm run parse:osm:all');
        return { phase: 'parse', dryRun: true, inputCount: inputFiles.length };
    }

    const t0 = Date.now();
    const r = spawnSync('node', [join(SCRIPTS_DIR, 'osm-parse.mjs'), '--all'], {
        cwd: ROOT,
        encoding: 'utf8',
        timeout: 180000,  // 3 min
    });
    const elapsed = ((Date.now() - t0) / 1000).toFixed(1);

    if (r.status !== 0) {
        log(`\n   ❌ Phase 2 failed in ${elapsed}s`);
        log((r.stderr || r.stdout || '').slice(-2000));
        throw new Error('Parse phase failed');
    }

    // Parse stdout for totals
    const stdout = r.stdout || '';
    const totalMatch = stdout.match(/Total:\s+(\d+)\/(\d+)\s+restaurants parsed across\s+(\d+)\s+files/);
    const totalParsed = totalMatch ? Number(totalMatch[1]) : null;
    const totalElements = totalMatch ? Number(totalMatch[2]) : null;
    const totalFiles = totalMatch ? Number(totalMatch[3]) : null;

    log(stdout.split('\n').filter(l => l.startsWith('   ') || l.startsWith('🎯') || l.startsWith('🌍')).join('\n'));
    log(`\n   📊 Phase 2 done in ${elapsed}s: parsed=${totalParsed ?? '?'} elements=${totalElements ?? '?'} files=${totalFiles ?? '?'}`);

    return { phase: 'parse', totalParsed, totalElements, totalFiles, elapsedSeconds: parseFloat(elapsed) };
}

// === Phase 3: Push ===
async function phase3Push({ log, dryRun, skipPush, onlyThis }) {
    if (skipPush) {
        log('⏭️  Skipping Phase 3 (--skip-push)');
        return { phase: 'push', skipped: true };
    }
    if (onlyThis && onlyThis !== 'push') {
        log('⏭️  Skipping Phase 3 (--only ' + onlyThis + ')');
        return { phase: 'push', skipped: true };
    }

    log('\n' + '━'.repeat(60));
    log('📤 Phase 3: Push all data/parsed/*.restaurants.json → Supabase');
    log('━'.repeat(60));

    // Count input files
    const parsedDir = join(DATA_DIR, 'parsed');
    const inputFiles = existsSync(parsedDir) ? readdirSync(parsedDir)
        .filter(f => /^osm-.*\.restaurants\.json$/.test(f)) : [];
    log(`   Input parsed files: ${inputFiles.length}`);

    if (dryRun) {
        log('   🧪 --dry-run: would run npm run push:osm:all --force');
        return { phase: 'push', dryRun: true, inputCount: inputFiles.length };
    }

    const t0 = Date.now();
    const r = spawnSync('node', [join(SCRIPTS_DIR, 'push-osm.mjs'), '--all', '--force'], {
        cwd: ROOT,
        encoding: 'utf8',
        timeout: 1800000,  // 30 min
    });
    const elapsed = ((Date.now() - t0) / 1000).toFixed(1);

    if (r.status !== 0) {
        log(`\n   ❌ Phase 3 failed in ${elapsed}s`);
        log((r.stderr || r.stdout || '').slice(-2000));
        throw new Error('Push phase failed');
    }

    // Parse stdout for summary
    const stdout = r.stdout || '';
    const totalMatch = stdout.match(/total rows inserted\s+(\d+)/);
    const totalInserted = totalMatch ? Number(totalMatch[1]) : null;
    const statusMatches = [...stdout.matchAll(/^\s+(\w+(?:-\w+)*)\s+(\d+)$/gm)];
    const statusCounts = Object.fromEntries(statusMatches.map(m => [m[1], Number(m[2])]));

    log(stdout.split('\n').filter(l => l.startsWith('   ') || l.startsWith('🎯') || l.startsWith('📊') || l.startsWith('🔍') || l.startsWith('🌍') || l.startsWith('📥') || l.startsWith('🗑️') || l.startsWith('🔥')).join('\n'));
    log(`\n   📊 Phase 3 done in ${elapsed}s: inserted=${totalInserted ?? '?'} statuses=${JSON.stringify(statusCounts)}`);

    return { phase: 'push', totalInserted, statusCounts, elapsedSeconds: parseFloat(elapsed) };
}

// === Phase 4: Verify ===
async function phase4Verify({ log, dryRun, onlyThis, env }) {
    if (onlyThis && onlyThis !== 'verify') {
        log('⏭️  Skipping Phase 4 (--only ' + onlyThis + ')');
        return { phase: 'verify', skipped: true };
    }
    if (dryRun) {
        log('⏭️  Skipping Phase 4 (--dry-run)');
        return { phase: 'verify', skipped: true };
    }

    log('\n' + '━'.repeat(60));
    log('🔍 Phase 4: Verify row counts in Supabase');
    log('━'.repeat(60));

    const baseUrl = env.SUPABASE_URL.replace(/\/$/, '');
    const key = env.SUPABASE_SERVICE_ROLE_KEY;

    const totalOsm = await countRows(baseUrl, key, 'restaurants', 'source=eq.osm');
    const totalProvinceLevel = await countRows(baseUrl, key, 'restaurants', 'source=eq.osm&district_id=is.null');
    const totalDistrictLevel = await countRows(baseUrl, key, 'restaurants', 'source=eq.osm&district_id=not.is.null');
    const totalAll = await countRows(baseUrl, key, 'restaurants');

    log(`   restaurants (all sources):          ${totalAll ?? '(error)'}`);
    log(`   restaurants (source=osm):           ${totalOsm ?? '(error)'}`);
    log(`     district_id IS NULL (province):   ${totalProvinceLevel ?? '(error)'}`);
    log(`     district_id IS NOT NULL:          ${totalDistrictLevel ?? '(error)'}`);

    return {
        phase: 'verify',
        totalAll, totalOsm, totalProvinceLevel, totalDistrictLevel,
    };
}

// === Utils ===
function sleep(ms) {
    return new Promise(r => setTimeout(r, ms));
}

// === Main ===
async function main() {
    const opts = parseArgs();
    if (opts.help) { printHelp(); return; }

    const startTs = new Date().toISOString().replace(/[:.]/g, '-');
    const logFile = join(DATA_DIR, `sweep-${startTs}.log`);
    const summaryFile = join(DATA_DIR, `sweep-${startTs}.summary.json`);

    const logLines = [];
    const log = (msg) => {
        const line = `${msg}`;
        console.log(line);
        logLines.push(line);
    };

    log('🌍 ThiengKin · OSM Sweep (M3.d)');
    log('━'.repeat(60));
    log(`   Started:    ${new Date().toISOString()}`);
    log(`   Options:    ${JSON.stringify(opts)}`);
    log(`   Log file:   ${relative(ROOT, logFile)}`);

    let env = null;
    if (!opts.dryRun && (opts.only === 'verify' || (!opts.skipPush && !opts.only))) {
        try {
            env = loadEnv();
            log(`   Supabase:   ${env.SUPABASE_URL}`);
        } catch (e) {
            log(`\n💥 ${e.message}`);
            process.exit(1);
        }
    }

    const summary = {
        startedAt: new Date().toISOString(),
        options: opts,
        phases: {},
    };

    try {
        // Phase 1: fetch
        if (!opts.skipFetch) {
            summary.phases.fetch = await phase1Fetch({ log, ...opts, onlyThis: opts.only, startFrom: opts.startFrom });
        }

        // Phase 2: parse
        summary.phases.parse = await phase2Parse({ log, ...opts, onlyThis: opts.only });

        // Phase 3: push
        summary.phases.push = await phase3Push({ log, ...opts, onlyThis: opts.only });

        // Phase 4: verify
        if (env) {
            summary.phases.verify = await phase4Verify({ log, ...opts, env });
        }

        summary.finishedAt = new Date().toISOString();
        summary.elapsedSeconds = (Date.parse(summary.finishedAt) - Date.parse(summary.startedAt)) / 1000;

        log('\n' + '━'.repeat(60));
        log(`✅ Sweep complete in ${summary.elapsedSeconds.toFixed(1)}s`);
        log('━'.repeat(60));

        writeFileSync(logFile, logLines.join('\n') + '\n');
        writeFileSync(summaryFile, JSON.stringify(summary, null, 2));
        log(`\n📋 Log:     ${relative(ROOT, logFile)}`);
        log(`📊 Summary: ${relative(ROOT, summaryFile)}`);
    } catch (e) {
        log(`\n💥 Fatal: ${e.message}`);
        if (e.stack) log(e.stack);
        summary.error = e.message;
        summary.finishedAt = new Date().toISOString();
        writeFileSync(logFile, logLines.join('\n') + '\n');
        writeFileSync(summaryFile, JSON.stringify(summary, null, 2));
        process.exit(1);
    }
}

main().catch(err => {
    console.error('\n💥 Unhandled:', err.message);
    if (err.stack) console.error(err.stack);
    process.exit(1);
});
