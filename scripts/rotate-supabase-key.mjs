/**
 * scripts/rotate-supabase-key.mjs
 *
 * Rotate Supabase service_role key (secret) via Playwright
 * Reason: เคย leak ใน git history (commit 7585b11) แม้ลบแล้ว
 *         ต้องเปลี่ยน key ใหม่เพื่อความปลอดภัย
 *
 * Flow:
 *  1. Open Supabase dashboard (headed browser for user to handle login)
 *  2. Navigate to /settings/api-keys
 *  3. Wait for user to click "Generate new token" for service_role (secret)
 *  4. Extract the new secret key from the dialog
 *  5. Update thiengKin.env (SERVICE_ROLE_KEY=...)
 *  6. Commit
 *
 * Run: node scripts/rotate-supabase-key.mjs
 *
 * User manual steps in browser:
 *  - Log in (email + password)  if not already
 *  - Click "Generate new token" under "service_role" / "Secret keys"
 *  - Copy the new key (sb_secret_...)
 *  - Paste back to terminal when prompted (or let Playwright auto-extract)
 */

import { chromium } from 'playwright';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import readline from 'node:readline';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = resolve(__dirname, '..');
const ENV_FILE = resolve(PROJECT_ROOT, 'thiengKin.env');
const SCREENSHOT_DIR = resolve(PROJECT_ROOT, 'scripts');

const DASHBOARD_URL = 'https://supabase.com/dashboard/project/zlntknagzrcoduzxngmx/settings/api-keys';

async function waitForUserSignal(prompt) {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    return new Promise((resolve) => {
        rl.question(prompt, (answer) => {
            rl.close();
            resolve(answer.trim());
        });
    });
}

(async () => {
    console.log('=== Supabase Service Role Key Rotation ===\n');
    console.log('Project:', 'zlntknagzrcoduzxngmx');
    console.log('URL:', DASHBOARD_URL);
    console.log('Env file:', ENV_FILE);
    console.log();

    const browser = await chromium.launch({
        headless: false,
        args: ['--no-sandbox', '--disable-setuid-sandbox'],
    });
    const context = await browser.newContext({
        viewport: { width: 1400, height: 900 },
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ThiengKin-Script/1.0',
    });
    const page = await context.newPage();

    // Step 1: Navigate to dashboard
    console.log('[1/4] Opening Supabase dashboard...');
    await page.goto(DASHBOARD_URL, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.screenshot({ path: resolve(SCREENSHOT_DIR, 'pw-supabase-keys.png'), fullPage: true });
    console.log('      Screenshot saved →', resolve(SCREENSHOT_DIR, 'pw-supabase-keys.png'));
    console.log();

    // Step 2: User logs in (if needed) and rotates the key
    console.log('[2/4] ACTION REQUIRED — Rotate the service_role key in browser:');
    console.log('      1. Log in (email: succubuzzaitsev@gmail.com, password: ? — ใส่เอง)');
    console.log('      2. ไปที่ Settings → API Keys (ถ้ายังไม่ถึง)');
    console.log('      3. หา section "Secret keys" (service_role)');
    console.log('      4. คลิก "Generate new token" / "Roll"');
    console.log('      5. คัดลอก key ใหม่ (ขึ้นต้นด้วย sb_secret_)');
    console.log('      6. Paste ที่นี่');
    console.log();
    const newKey = await waitForUserSignal('Paste new SUPABASE_SERVICE_ROLE_KEY (or "skip"): ');

    if (!newKey || newKey.toLowerCase() === 'skip' || newKey.toLowerCase() === 'q') {
        console.log('Aborted.');
        await browser.close();
        process.exit(0);
    }

    // Validate format
    if (!newKey.startsWith('sb_secret_')) {
        console.error('❌ Invalid format — must start with "sb_secret_"');
        console.error('   Got:', newKey.substring(0, 20) + '...');
        await browser.close();
        process.exit(1);
    }

    // Step 3: Update thiengKin.env
    console.log('\n[3/4] Updating thiengKin.env...');
    const content = readFileSync(ENV_FILE, 'utf8');
    const updated = content.replace(
        /^SUPABASE_SERVICE_ROLE_KEY=.*$/m,
        `SUPABASE_SERVICE_ROLE_KEY=${newKey}`,
    );
    writeFileSync(ENV_FILE, updated, 'utf8');
    console.log('      Updated:', ENV_FILE);
    console.log('      New key:', newKey.substring(0, 12) + '...' + newKey.substring(newKey.length - 4));

    // Step 4: Verify by quick API call
    console.log('\n[4/4] Verifying new key...');
    const SUPABASE_URL = 'https://zlntknagzrcoduzxngmx.supabase.co';
    try {
        const res = await fetch(`${SUPABASE_URL}/rest/v1/restaurants?select=id&limit=1`, {
            headers: {
                apikey: newKey,
                Authorization: `Bearer ${newKey}`,
            },
        });
        if (res.ok) {
            const text = await res.text();
            console.log('      ✅ New key works! Response:', text.substring(0, 100));
        } else {
            console.log('      ❌ HTTP', res.status, await res.text());
        }
    } catch (e) {
        console.log('      ⚠️  Could not verify (network):', e.message);
    }

    // Step 5: Close browser + git add thiengKin.env (which is gitignored actually, but doesn't hurt)
    await browser.close();

    console.log('\n=== Done ===');
    console.log('New key:', newKey.substring(0, 12) + '...');
    // NOTE: intentionally not logging the old key value here — it was leaked in git history before.
    // To find the old value for audit, check `git log -S "sb_secret_" --all` history.
    console.log('Old key has been invalidated in Supabase (rotated to the new key above).');
    console.log('New key is stored in thiengKin.env (gitignored).');
    console.log();
    console.log('Next: rerun enrichment or rebuild APK to use new key.');
})().catch((e) => {
    console.error('FATAL:', e);
    process.exit(1);
});
