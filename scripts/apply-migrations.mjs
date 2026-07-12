#!/usr/bin/env node
/**
 * Apply Supabase migrations directly via Postgres connection.
 * Uses the pooler (port 6543) and the project's db password.
 *
 * Env (from thiengKin.env or process env):
 *   - SUPABASE_URL                (required)
 *   - SUPABASE_DB_PASSWORD        (recommended — postgres DB password)
 *   - ACCOUNT_PASSWORD            (legacy fallback — old name when DB password
 *                                  equaled the Supabase account login password.
 *                                  Prefer SUPABASE_DB_PASSWORD now that they
 *                                  are rotated separately.)
 *
 * Usage: node scripts/apply-migrations.mjs [001_initial_schema.sql] [002_rls_policies.sql] ...
 *        (no args = apply all migrations in supabase/migrations/ in order)
 */
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const MIGRATIONS_DIR = join(ROOT, 'supabase', 'migrations');
const ENV_FILE = existsSync(join(ROOT, 'thiengKin.env'))
    ? join(ROOT, 'thiengKin.env')
    : join(ROOT, '.env');

function loadEnv() {
    if (!existsSync(ENV_FILE)) {
        throw new Error(`Missing env file: ${ENV_FILE}`);
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

const env = loadEnv();
const url = env.SUPABASE_URL;
if (!url) throw new Error('SUPABASE_URL missing in .env');

// Project ref from URL: https://zlntknagzrcoduzxngmx.supabase.co → zlntknagzrcoduzxngmx
const m = url.match(/https:\/\/([^.]+)\.supabase\.co/);
if (!m) throw new Error(`Cannot parse project ref from SUPABASE_URL: ${url}`);
const ref = m[1];

const dbPassword = env.SUPABASE_DB_PASSWORD || env.ACCOUNT_PASSWORD || process.env.ACCOUNT_PASSWORD || process.env.SUPABASE_DB_PASSWORD;
if (!dbPassword) {
    throw new Error('Missing SUPABASE_DB_PASSWORD or ACCOUNT_PASSWORD in .env or process env');
}

// Use pooler (port 6543) — direct (5432) not reachable from this machine
const connStr = `postgresql://postgres.${ref}:${encodeURIComponent(dbPassword)}@aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres`;

const requested = process.argv.slice(2);
const testConnection = requested.includes('--test-connection') || requested.includes('--dry-run');
const files = requested.filter(a => !a.startsWith('--')).length
    ? requested.filter(a => !a.startsWith('--')).map(f => join(ROOT, f))
    : readdirSync(MIGRATIONS_DIR)
        .filter(f => f.endsWith('.sql'))
        .sort()
        .map(f => join(MIGRATIONS_DIR, f));

(async () => {
    console.log(`[apply-migrations] Connecting to project ${ref} (pooler:6543)...`);
    const client = new pg.Client({ connectionString: connStr, ssl: { rejectUnauthorized: false } });
    await client.connect();
    console.log('[apply-migrations] Connected.');

    if (testConnection) {
        try {
            const r = await client.query('SELECT version() AS v, current_user AS u, current_database() AS d');
            console.log(`[apply-migrations] Server version: ${r.rows[0].v.split(',')[0]}`);
            console.log(`[apply-migrations] Connected as user: ${r.rows[0].u}`);
            console.log(`[apply-migrations] Database: ${r.rows[0].d}`);
            console.log('\n[apply-migrations] Connection test PASSED. (No migrations applied.)');
        } catch (e) {
            console.error(`[apply-migrations] Connection test FAILED: ${e.message}`);
            await client.end();
            process.exit(1);
        }
        await client.end();
        process.exit(0);
    }

    let totalApplied = 0;
    let totalFailed = 0;

    for (const file of files) {
        if (!existsSync(file)) {
            console.error(`  ✗ File not found: ${file}`);
            totalFailed++;
            continue;
        }
        const sql = readFileSync(file, 'utf8');
        const label = file.split(/[\\/]/).pop();
        console.log(`\n[apply-migrations] Applying ${label} (${sql.length} bytes)...`);
        try {
            await client.query(sql);
            console.log(`  ✓ ${label} applied successfully`);
            totalApplied++;
        } catch (e) {
            console.error(`  ✗ ${label} FAILED: ${e.message}`);
            totalFailed++;
        }
    }

    await client.end();

    console.log(`\n[apply-migrations] Done — applied=${totalApplied} failed=${totalFailed}`);
    process.exit(totalFailed > 0 ? 1 : 0);
})();
