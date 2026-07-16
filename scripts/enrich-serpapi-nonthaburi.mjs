/**
 * scripts/enrich-serpapi-nonthaburi.mjs
 *
 * Enrichment: SerpApi Google Maps — เพิ่มร้านจาก Google Maps (with photos)
 * มา push ขิง Supabase (source='serpapi') เพื่อให้ Android app แสดงรูป+รีวิวจริง
 *
 * Free tier: 250 searches/month. This script uses ~3 search + N photos calls.
 *
 * Pipeline:
 *  1. Google Maps search (paginated) — list of restaurants in Nonthaburi bbox
 *  2. Per-result: Google Maps Photos engine — first photo URL + thumbnail
 *  3. Convert to ThiengKin schema
 *  4. Push to Supabase (on_conflict=id, idempotent)
 *
 * Run: node scripts/enrich-serpapi-nonthaburi.mjs [--dry-run] [--force]
 *   --dry-run: print + save JSON, no DB write
 *   --force:   delete existing serpapi rows before re-insert
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = resolve(__dirname, '..');

// ===== Load SERPAPI_KEY from thiengKin.env =====
function loadEnv() {
    const envPath = resolve(PROJECT_ROOT, 'thiengKin.env');
    const content = readFileSync(envPath, 'utf8');
    const map = {};
    for (const line of content.split('\n')) {
        const m = line.match(/^([A-Z_][A-Z0-9_]*)=(.*)$/);
        if (m) map[m[1]] = m[2].trim();
    }
    return map;
}

const env = loadEnv();
const SERPAPI_KEY = env.SERPAPI_KEY;
const SUPABASE_URL = env.SUPABASE_URL;
const SUPABASE_KEY = env.SUPABASE_SERVICE_ROLE_KEY;

if (!SERPAPI_KEY) {
    console.error('❌ SERPAPI_KEY not found in thiengKin.env');
    console.error('   Get free key at https://serpapi.com/ (250 searches/mo)');
    process.exit(1);
}
if (!SUPABASE_URL || !SUPABASE_KEY) {
    console.error('❌ SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY missing in thiengKin.env');
    process.exit(1);
}

const args = process.argv.slice(2);
const DRY_RUN = args.includes('--dry-run');
const FORCE = args.includes('--force');

// ===== Config =====
const SEARCH_QUERY = 'restaurant';
const NONTHABURI_CENTER = '@13.964,100.415,11z';  // lat, lng, zoom 11
const NONTHABURI_PROVINCE_ID = 'nonthaburi';
const MAX_PAGES = 3;  // 3 × 20 = 60 restaurants (saves quota)
const REQUESTS_PER_PAGE = 20;

// ===== Supabase REST helpers (no @supabase/supabase-js) =====
async function supabaseQuery(path, options = {}) {
    const url = `${SUPABASE_URL}/rest/v1/${path}`;
    const res = await fetch(url, {
        ...options,
        headers: {
            apikey: SUPABASE_KEY,
            Authorization: `Bearer ${SUPABASE_KEY}`,
            'Content-Type': 'application/json',
            ...(options.headers || {}),
        },
    });
    const text = await res.text();
    if (!res.ok) {
        throw new Error(`Supabase ${res.status}: ${text.substring(0, 300)}`);
    }
    return text ? JSON.parse(text) : null;
}

// ===== SerpApi helpers =====
async function serpapiGet(params) {
    const url = new URL('https://serpapi.com/search.json');
    for (const [k, v] of Object.entries(params)) {
        url.searchParams.set(k, v);
    }
    const res = await fetch(url);
    const text = await res.text();
    if (!res.ok) {
        throw new Error(`SerpApi ${res.status}: ${text.substring(0, 300)}`);
    }
    return JSON.parse(text);
}

// ===== Mapping helpers =====

// Map SerpApi Thai/English type → ThiengKin category (existing OSM categories)
function mapCategory(serpType) {
    if (!serpType) return 'ร้านอาหาร';
    const t = serpType.toLowerCase();
    if (t.includes('กาแฟ') || t.includes('coffee') || t.includes('cafe')) return 'คาเฟ่';
    if (t.includes('เบเกอรี่') || t.includes('bakery') || t.includes('bake')) return 'เบเกอรี่';
    if (t.includes('ฟาสต์ฟู้ด') || t.includes('fast food') || t.includes('fast_food')) return 'ฟาสต์ฟู้ด';
    if (t.includes('ผับ') || t.includes('pub') || t.includes('bar') || t.includes('nightclub')) return 'ผับบาร์';
    if (t.includes('ไอศกรีม') || t.includes('ice cream') || t.includes('dessert') || t.includes('ของหวาน') || t.includes('bingsu')) return 'ของหวาน';
    if (t.includes('สลัด') || t.includes('salad')) return 'สลัด';
    if (t.includes('ก๋วยเตี๋ยว') || t.includes('noodle') || t.includes('บะหมี่') || t.includes('ราเมง') || t.includes('ramen')) return 'ก๋วยเตี๋ยว';
    if (t.includes('ซูชิ') || t.includes('sushi') || t.includes('ญี่ปุ่น') || t.includes('japanese') || t.includes('ramen')) return 'ซูชิ';
    if (t.includes('ปิ้งย่าง') || t.includes('bbq') || t.includes('grill') || t.includes('korean')) return 'ปิ้งย่าง';
    return 'ร้านอาหาร';  // default
}

// Map SerpApi "฿200-1,000" or "$" → priceLevel int (0-4)
function mapPriceLevel(price) {
    if (!price) return null;
    const p = String(price);
    if (p.includes('$$$$') || p.includes('฿฿฿฿')) return 4;
    if (p.includes('$$$') || p.includes('฿฿฿')) return 3;
    if (p.includes('$$') || p.includes('฿฿')) return 2;
    if (p.includes('$') || p.includes('฿')) return 1;
    return null;
}

// Extract tags from SerpApi extensions (popular_for, offerings, accessibility, etc.)
function extractTags(extensions) {
    if (!extensions || !Array.isArray(extensions)) return [];
    const tags = [];
    for (const ext of extensions) {
        for (const [category, value] of Object.entries(ext)) {
            if (typeof value === 'string') {
                // value is a comma-separated list
                for (const item of value.split(/[,、]/).map(s => s.trim()).filter(Boolean)) {
                    // Map common Thai categories to short tags
                    if (category === 'service_options' && item.includes('นั่ง')) tags.push('dine_in');
                    else if (category === 'service_options' && item.includes('สั่ง')) tags.push('takeout');
                    else if (category === 'service_options' && item.includes('ส่ง')) tags.push('delivery');
                    else if (category === 'popular_for' && item.includes('เช้า')) tags.push('breakfast');
                    else if (category === 'popular_for' && item.includes('กลางวัน')) tags.push('lunch');
                    else if (category === 'popular_for' && item.includes('ค่ำ')) tags.push('dinner');
                    else if (category === 'offerings' && item.includes('มังสวิรัติ')) tags.push('vegetarian');
                    else if (category === 'planning' && item.includes('จอง')) tags.push('reservation');
                }
            }
        }
    }
    return [...new Set(tags)];
}

// Convert operating_hours object to OSM-style opening_hours string
function mapOpeningHours(operatingHours) {
    if (!operatingHours || typeof operatingHours !== 'object') return null;
    // OSM format: "Mo-Fr 07:00-22:00; Sa-Su 11:00-23:00"
    // SerpApi uses Thai day names. Approximate mapping:
    const dayMap = {
        'วันจันทร์': 'Mo', 'วันอังคาร': 'Tu', 'วันพุธ': 'We',
        'วันพฤหัสบดี': 'Th', 'วันศุกร์': 'Fr', 'วันเสาร์': 'Sa', 'วันอาทิตย์': 'Su',
    };
    const lines = [];
    for (const [thaiDay, value] of Object.entries(operatingHours)) {
        if (value.includes('24 ชั่วโมง')) {
            const osm = dayMap[thaiDay] || thaiDay;
            lines.push(`${osm} 00:00-24:00`);
        } else {
            // Try to parse "10 AM–5 AM" or "07:00-22:00"
            const m = value.match(/(\d{1,2})[:.]?(\d{2})?\s*[–-]\s*(\d{1,2})[:.]?(\d{2})?/);
            if (m) {
                const [, h1, m1 = '00', h2, m2 = '00'] = m;
                const osm = dayMap[thaiDay] || thaiDay;
                lines.push(`${osm} ${h1.padStart(2, '0')}:${m1}-${h2.padStart(2, '0')}:${m2}`);
            }
        }
    }
    return lines.length > 0 ? lines.join('; ') : null;
}

// Deterministic ID from source + source_id
function makeId(source, sourceId) {
    // prefix source to avoid collisions with OSM IDs
    return `serpapi_${sourceId.substring(0, 32)}`;
}

// Convert SerpApi local_result to ThiengKin row
function toThiengKinRow(local, photosData) {
    const photos = photosData?.photos || [];
    const firstPhoto = photos[0];
    // photo URL: prefer serpapi_thumbnail (SerpApi CDN) > thumbnail (Google CDN)
    const photoUrl = local.serpapi_thumbnail || local.thumbnail || firstPhoto?.image || null;

    return {
        id: makeId('serpapi', local.place_id),
        name: local.title || 'Unknown',
        name_th: local.title,  // SerpApi returns Thai if hl=th
        name_en: null,  // SerpApi doesn't give English name
        category: mapCategory(local.type),
        lat: local.gps_coordinates?.latitude ?? null,
        lng: local.gps_coordinates?.longitude ?? null,
        address: local.address || null,
        phone: local.phone || null,
        rating: local.rating ?? null,
        review_count: local.reviews ?? 0,
        source: 'serpapi',
        source_id: local.place_id,
        photo_url: photoUrl,
        tags: extractTags(local.extensions),
        opening_hours: mapOpeningHours(local.operating_hours) || local.hours || null,
        price_level: mapPriceLevel(local.price) || local.extracted_price || null,
        description: null,  // SerpApi doesn't return description in search
        province_id: NONTHABURI_PROVINCE_ID,
        district_id: null,  // Will be filled in Phase 2 with reverse-geocode matching
    };
}

// ===== Main pipeline =====
console.log('=== SerpApi Nonthaburi Enrichment ===\n');
console.log('Config:');
console.log('  Search:', SEARCH_QUERY, '|', NONTHABURI_CENTER);
console.log('  Pages:', MAX_PAGES, '×', REQUESTS_PER_PAGE, '=', MAX_PAGES * REQUESTS_PER_PAGE, 'max restaurants');
console.log('  Quota: ~', MAX_PAGES, '+ photos = ~', MAX_PAGES + MAX_PAGES * REQUESTS_PER_PAGE, 'calls (well within 250/mo)');
console.log('  Dry run:', DRY_RUN);
console.log('  Force:', FORCE);
console.log();

// Step 1: Search (paginated)
console.log('[1/4] Searching restaurants...');
const allResults = [];
for (let page = 0; page < MAX_PAGES; page++) {
    const start = page * REQUESTS_PER_PAGE;
    console.log(`      Page ${page + 1}/${MAX_PAGES} (start=${start})...`);
    const resp = await serpapiGet({
        engine: 'google_maps',
        q: SEARCH_QUERY,
        ll: NONTHABURI_CENTER,
        type: 'search',
        start: start,
        api_key: SERPAPI_KEY,
        hl: 'th',
    });
    if (resp.search_metadata?.status !== 'Success') {
        console.log(`      ⚠️  Page ${page + 1} status: ${resp.search_metadata?.status}`);
        break;
    }
    const results = resp.local_results || [];
    console.log(`      Got ${results.length} results (${resp.search_metadata.total_time_taken}s)`);
    allResults.push(...results);
    // Stop if fewer than 20 (no more pages)
    if (results.length < REQUESTS_PER_PAGE) break;
}
console.log(`      Total: ${allResults.length} restaurants\n`);

// Step 2: Fetch photos for each (1 call per place)
console.log('[2/4] Fetching photos...');
const photosCache = new Map();
let photoCalls = 0;
for (let i = 0; i < allResults.length; i++) {
    const r = allResults[i];
    if (!r.photos_link) {
        photosCache.set(r.place_id, { photos: [] });
        continue;
    }
    try {
        const photoResp = await serpapiGet({
            engine: 'google_maps_photos',
            data_id: r.data_id,
            api_key: SERPAPI_KEY,
            hl: 'th',
        });
        photosCache.set(r.place_id, photoResp);
        photoCalls++;
        if (i % 10 === 0) console.log(`      ${i + 1}/${allResults.length} (${photoCalls} photo calls so far)`);
    } catch (e) {
        console.log(`      ⚠️  Photo fetch failed for ${r.title}: ${e.message.substring(0, 100)}`);
        photosCache.set(r.place_id, { photos: [] });
    }
    // Be nice to SerpApi rate limit
    await new Promise(resolve => setTimeout(resolve, 200));
}
console.log(`      Total photo calls: ${photoCalls}\n`);

// Step 3: Convert to ThiengKin schema
console.log('[3/4] Converting to ThiengKin schema...');
const rows = allResults.map(r => toThiengKinRow(r, photosCache.get(r.place_id)));
console.log(`      Converted ${rows.length} rows`);
console.log(`      Sample: ${rows[0]?.name} | ${rows[0]?.category} | ⭐${rows[0]?.rating} | ${rows[0]?.photo_url?.substring(0, 60)}...`);

// Save raw + processed JSON
const rawPath = resolve(PROJECT_ROOT, 'data/parsed/serpapi-nonthaburi-raw.json');
const processedPath = resolve(PROJECT_ROOT, 'data/parsed/serpapi-nonthaburi-processed.json');
writeFileSync(rawPath, JSON.stringify(allResults, null, 2), 'utf8');
writeFileSync(processedPath, JSON.stringify(rows, null, 2), 'utf8');
console.log(`      Raw: ${rawPath}`);
console.log(`      Processed: ${processedPath}\n`);

// Step 4: Push to Supabase
if (DRY_RUN) {
    console.log('[4/4] DRY RUN — skipping Supabase push');
    console.log('      Run without --dry-run to push to DB');
} else {
    console.log('[4/4] Pushing to Supabase...');
    if (FORCE) {
        console.log('      --force: deleting existing serpapi rows for Nonthaburi...');
        const deleted = await supabaseQuery('restaurants?source=eq.serpapi&province_id=eq.nonthaburi', {
            method: 'DELETE',
        });
        console.log(`      Deleted: ${deleted?.length ?? '?'} rows`);
    }
    // Upsert in batches of 50
    const BATCH_SIZE = 50;
    let pushed = 0;
    for (let i = 0; i < rows.length; i += BATCH_SIZE) {
        const batch = rows.slice(i, i + BATCH_SIZE);
        const result = await supabaseQuery('restaurants?on_conflict=id', {
            method: 'POST',
            headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
            body: JSON.stringify(batch),
        });
        pushed += batch.length;
        console.log(`      Batch ${Math.floor(i / BATCH_SIZE) + 1}: ${batch.length} rows`);
    }
    console.log(`      Total pushed: ${pushed} rows`);
}

console.log('\n=== Done ===');
console.log('Stats:');
console.log(`  Restaurants: ${allResults.length}`);
console.log(`  SerpApi calls: ${MAX_PAGES + photoCalls} (search + photos)`);
console.log(`  Rows in DB: ${DRY_RUN ? '(dry run)' : rows.length}`);
