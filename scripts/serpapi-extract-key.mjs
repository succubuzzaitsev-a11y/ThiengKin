/**
 * SerpApi Sign-up Helper
 *
 * Flow:
 *  1. Open serpapi.com/users/sign_up in headed browser
 *  2. Take screenshot — user fills email + password in visible browser
 *  3. User clicks "Create Account" + verifies email themselves
 *  4. After user signals "done" (we wait for them to type at the prompt),
 *     navigate to dashboard, extract API key, save to .env
 *
 * Run: node scripts/serpapi-extract-key.mjs
 *
 * NOTE: Uses Playwright with persistent context so cookies survive across
 * the "pause for user" gap. Browser window stays open the whole time.
 */

import { chromium } from 'playwright';
import { writeFileSync, readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import readline from 'node:readline';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = resolve(__dirname, '..');
const ENV_FILE = resolve(PROJECT_ROOT, 'thiengKin.env');  // append to existing env (already gitignored)
const SCREENSHOT_DIR = resolve(PROJECT_ROOT, 'scripts');

const SIGNUP_URL = 'https://serpapi.com/users/sign_up';
const DASHBOARD_URL = 'https://serpapi.com/dashboard';
const EMAIL = 'succubuzzaitsev@gmail.com';  // M2 Supabase project email

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
    console.log('=== SerpApi Sign-up + Key Extract Helper ===\n');
    console.log('Email:', EMAIL);
    console.log('Project:', PROJECT_ROOT);
    console.log('Env file:', ENV_FILE);
    console.log();

    const browser = await chromium.launch({
        headless: false,  // visible window for user to interact
        args: ['--no-sandbox', '--disable-setuid-sandbox'],
    });
    const context = await browser.newContext({
        viewport: { width: 1280, height: 800 },
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ThiengKin/0.1.0',
    });
    const page = await context.newPage();

    // Step 1: Navigate to signup
    console.log('[1/4] Navigating to signup page...');
    await page.goto(SIGNUP_URL, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.screenshot({ path: resolve(SCREENSHOT_DIR, 'pw-serpapi-signup.png'), fullPage: true });
    console.log('      Screenshot saved →', resolve(SCREENSHOT_DIR, 'pw-serpapi-signup.png'));
    console.log();

    // Step 2: User signs up manually in the visible browser
    console.log('[2/4] ACTION REQUIRED — Complete sign-up in the visible browser:');
    console.log('      1. Enter email:', EMAIL);
    console.log('      2. Choose a password (any — you can change later)');
    console.log('      3. Click "Create Account" / "Sign up"');
    console.log('      4. Check inbox at', EMAIL, '→ click verification link');
    console.log('      5. After verification, return here and press Enter');
    console.log();
    const phase1 = await waitForUserSignal('Press Enter after verifying email... ');
    if (phase1.toLowerCase() === 'q' || phase1.toLowerCase() === 'quit') {
        console.log('Aborted.');
        await browser.close();
        process.exit(0);
    }

    // Step 3: Navigate to dashboard, extract API key
    console.log('\n[3/4] Navigating to dashboard, extracting API key...');
    await page.goto(DASHBOARD_URL, { waitUntil: 'networkidle', timeout: 30000 });
    await page.screenshot({ path: resolve(SCREENSHOT_DIR, 'pw-serpapi-dashboard.png'), fullPage: true });

    // Try multiple selectors for the API key field
    const apiKeySelectors = [
        'input[readonly][value*=""]',  // Readonly input with API key
        'input[name*="api_key" i]',
        'input[data-testid*="api" i]',
        'code:has-text("api")',
        'pre:has-text("api")',
        'tt:has-text("api")',
    ];

    let apiKey = null;
    for (const selector of apiKeySelectors) {
        try {
            const el = await page.$(selector);
            if (el) {
                const value = await el.evaluate((node) => {
                    if (node.tagName === 'INPUT' || node.tagName === 'TEXTAREA') {
                        return node.value || node.placeholder;
                    }
                    return node.textContent?.trim();
                });
                if (value && value.length > 20 && value.length < 200 && /^[a-z0-9]+$/i.test(value)) {
                    apiKey = value;
                    console.log('      Found via', selector);
                    break;
                }
            }
        } catch (e) {
            // continue trying
        }
    }

    // Fallback: try grabbing from any "show/hide" or copy button near "api key" text
    if (!apiKey) {
        console.log('      Trying text-based extraction...');
        apiKey = await page.evaluate(() => {
            // Look for any element that resembles an API key (40-100 chars, hex/alphanumeric)
            const all = document.querySelectorAll('input, code, pre, tt, span, div');
            for (const el of all) {
                const text = (el.value || el.textContent || '').trim();
                if (text.length >= 32 && text.length <= 128 && /^[a-z0-9]+$/i.test(text) && !text.includes(' ')) {
                    return text;
                }
            }
            return null;
        });
    }

    if (!apiKey) {
        console.log('      ⚠️  Could not auto-extract API key.');
        console.log('      The dashboard screenshot is at:', resolve(SCREENSHOT_DIR, 'pw-serpapi-dashboard.png'));
        console.log('      Please copy the API key manually and paste it here.');
        const manual = await waitForUserSignal('Paste API key (or "skip"): ');
        if (manual && manual.toLowerCase() !== 'skip') {
            apiKey = manual.trim();
        }
    }

    // Step 4: Save to env file (append to existing thiengKin.env)
    console.log('\n[4/4] Saving API key...');
    if (apiKey) {
        let existing = '';
        if (existsSync(ENV_FILE)) {
            existing = readFileSync(ENV_FILE, 'utf8');
        }
        // Remove old SERPAPI_KEY if present
        existing = existing.replace(/^SERPAPI_KEY=.*\n?/gm, '');
        const updated = existing.trimEnd() + `\n\n# SerpApi (added ${new Date().toISOString()})\nSERPAPI_KEY=${apiKey}\n`;
        writeFileSync(ENV_FILE, updated, 'utf8');
        console.log('      Appended to', ENV_FILE);
        console.log('      API key:', apiKey.substring(0, 8) + '...' + apiKey.substring(apiKey.length - 4));
    } else {
        console.log('      No API key to save. Please re-run and provide manually.');
    }

    await browser.close();
    console.log('\n=== Done ===');
    if (apiKey) {
        console.log('Next step: test with one query against Nonthaburi to verify key works.');
    }
})().catch((e) => {
    console.error('FATAL:', e);
    process.exit(1);
});
