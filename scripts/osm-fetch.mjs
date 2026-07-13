// osm-fetch.mjs
// เที่ยงกิน · M3.a — OpenStreetMap Overpass fetcher
//
// ดึงร้านอาหาร/คาเฟ่/ฟาสต์ฟู้ด/ศูนย์อาหาร จาก OSM Overpass API
// Output: data/osm-<name>.json (raw Overpass response, parse ด้วย OsmImporter.kt)
//
// Usage:
//   node scripts/osm-fetch.mjs                       # default: chiang_mai city bbox
//   node scripts/osm-fetch.mjs chiang_mai            # province bbox (จาก thailand-geography.json)
//   node scripts/osm-fetch.mjs chiang_mai --city     # city bbox (override)
//   node scripts/osm-fetch.mjs 18.70 98.85 18.90 99.10 custom-name
//   node scripts/osm-fetch.mjs --list                # list จังหวัดที่รู้จัก
//
// Overpass QL (เหมือน OsmClient.kt):
//   [out:json][timeout:60];
//   (
//     node["amenity"~"restaurant|cafe|fast_food|food_court"](bbox);
//     way["amenity"~"restaurant|cafe|fast_food|food_court"](bbox);
//   );
//   out body; >; out skel qt;
//
// Rate limit (Overpass wiki 2026): 10,000 req/day per endpoint, 1 GB download
//   เราใช้ 1 call/จังหวัด/refresh → 77 calls = 1 cycle ต่อ endpoint
//   Fallback chain: 3 endpoints กระจายโหลด (rate limit เป็นต่อ instance)

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '..');

/** Overpass endpoint chain — try in order, fall back on 429/504/timeout */
const OVERPASS_ENDPOINTS = [
    'https://overpass-api.de/api/interpreter',
    'https://overpass.kumi.systems/api/interpreter',
    'https://overpass.openstreetmap.fr/api/interpreter',
];
const USER_AGENT = 'ThiengKin/0.1.0 (osm-fetch; +https://thiengkin.app)';
const GEOGRAPHY_FILE = path.join(ROOT, 'data', 'thailand-geography.json');

// City bbox presets (subset ของ province bbox — query เร็วกว่า 5-20 เท่า)
const CITY_BBOX = {
  chiang_mai: { s: 18.70, w: 98.85, n: 18.90, e: 99.10, label: 'Chiang Mai city' },
  bangkok:    { s: 13.65, w: 100.35, n: 13.95, e: 100.80, label: 'Bangkok metro' },
  phuket:     { s: 7.75, w: 98.30, n: 7.95, e: 98.50, label: 'Phuket town' },
  chiang_rai: { s: 19.85, w: 99.75, n: 20.05, e: 100.00, label: 'Chiang Rai city' },
  pai:        { s: 19.30, w: 98.40, n: 19.40, e: 98.50, label: 'Pai town' },
};

/** Build Overpass QL — match OsmClient.kt query shape (FIXED: out body center) */
function buildQuery(bbox) {
  return `[out:json][timeout:60];
(
  node["amenity"~"restaurant|cafe|fast_food|food_court"](${bbox.s},${bbox.w},${bbox.n},${bbox.e});
  way["amenity"~"restaurant|cafe|fast_food|food_court"](${bbox.s},${bbox.w},${bbox.n},${bbox.e});
);
out body center;
>;
out skel qt;
`;
}

async function fetchOverpass(query, { endpointIdx = 0, triedEndpoints = [] } = {}) {
  if (endpointIdx >= OVERPASS_ENDPOINTS.length) {
    throw new Error(`All ${OVERPASS_ENDPOINTS.length} Overpass endpoints failed. Tried: ${triedEndpoints.join(', ')}`);
  }
  const url = OVERPASS_ENDPOINTS[endpointIdx];
  const body = new URLSearchParams({ data: query }).toString();
  let res;
  try {
    res = await fetch(url, {
      method: 'POST',
      headers: {
        'User-Agent': USER_AGENT,
        'Content-Type': 'application/x-www-form-urlencoded',
        'Accept': 'application/json',
      },
      body,
      signal: AbortSignal.timeout(75000),  // 75s
    });
  } catch (e) {
    const next = endpointIdx + 1;
    console.warn(`   ⚠️  ${url} — ${e.message}; trying next endpoint`);
    return fetchOverpass(query, { endpointIdx: next, triedEndpoints: [...triedEndpoints, url] });
  }
  if (res.status === 429 || res.status === 504 || res.status === 502 || res.status === 503) {
    const next = endpointIdx + 1;
    if (next < OVERPASS_ENDPOINTS.length) {
      console.warn(`   ⚠️  ${url} → HTTP ${res.status}; trying next endpoint`);
      return fetchOverpass(query, { endpointIdx: next, triedEndpoints: [...triedEndpoints, url] });
    }
    const text = await res.text();
    throw new Error(`All endpoints failed. Last: ${url} HTTP ${res.status}: ${text.substring(0, 200)}`);
  }
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Overpass HTTP ${res.status} (${url}): ${text.substring(0, 200)}`);
  }
  if (endpointIdx > 0) {
    console.log(`   ✓ Recovered via ${url} (endpoint #${endpointIdx + 1})`);
  }
  return res.json();
}

/** Parse CLI args → { bbox, name, useCity, help, list } */
function parseArgs() {
  const args = process.argv.slice(2);
  if (args.includes('--help') || args.includes('-h')) {
    return { help: true };
  }
  if (args.includes('--list')) {
    return { list: true };
  }
  if (args.length >= 4 && !isNaN(parseFloat(args[0]))) {
    // raw bbox
    return {
      bbox: { s: +args[0], w: +args[1], n: +args[2], e: +args[3] },
      name: args[4] || 'custom',
    };
  }
  // province name (or empty = default chiang_mai) + optional --city flag
  const useCity = args.includes('--city');
  const provinceId = args.find(a => !a.startsWith('--')) || 'chiang_mai';
  return { provinceId, useCity };
}

function resolveBbox({ provinceId, useCity }) {
  if (provinceId && useCity && CITY_BBOX[provinceId]) {
    return { bbox: CITY_BBOX[provinceId], name: `${provinceId.replace(/_/g, '-')}-city`, label: CITY_BBOX[provinceId].label };
  }
  if (provinceId && !useCity) {
    // try geography file for full province bbox
    if (fs.existsSync(GEOGRAPHY_FILE)) {
      const geo = JSON.parse(fs.readFileSync(GEOGRAPHY_FILE, 'utf8'));
      const p = geo.provinces?.find(x => x.id === provinceId);
      if (p?.bbox) {
        return { bbox: p.bbox, name: provinceId.replace(/_/g, '-'), label: p.nameEn || p.id };
      }
    }
    // province not in geography file → fall back to city preset if available
    if (CITY_BBOX[provinceId]) {
      console.warn(`⚠️  Province '${provinceId}' not found in ${GEOGRAPHY_FILE} — falling back to city preset`);
      return { bbox: CITY_BBOX[provinceId], name: `${provinceId.replace(/_/g, '-')}-city`, label: CITY_BBOX[provinceId].label };
    }
    throw new Error(`Province '${provinceId}' not found in ${GEOGRAPHY_FILE} and no CITY_BBOX preset available`);
  }
  // default = chiang_mai city
  return { bbox: CITY_BBOX.chiang_mai, name: 'chiangmai-city', label: CITY_BBOX.chiang_mai.label };
}

function printHelp() {
  console.log(`🌍 ThiengKin · OSM Overpass Fetcher (M3.a)

Usage:
  node scripts/osm-fetch.mjs                       # default: chiang_mai city
  node scripts/osm-fetch.mjs <provinceId>          # full province bbox
  node scripts/osm-fetch.mjs <provinceId> --city   # city preset
  node scripts/osm-fetch.mjs <s> <w> <n> <e> [name]  # raw bbox
  node scripts/osm-fetch.mjs --list                # list city presets

Output: data/osm-<name>.json (raw Overpass response)
        parse ด้วย android/.../OsmImporter.kt ใน M3.b

City presets: ${Object.keys(CITY_BBOX).join(', ')}
`);
}

function printList() {
  console.log('City presets:');
  for (const [id, b] of Object.entries(CITY_BBOX)) {
    console.log(`  ${id.padEnd(12)} s=${b.s} w=${b.w} n=${b.n} e=${b.e}  (${b.label})`);
  }
}

function printStats(elements) {
  const nodes = elements.filter(e => e.type === 'node');
  const ways = elements.filter(e => e.type === 'way');
  const withName = elements.filter(e => e.tags?.name);
  const withNameTh = elements.filter(e => e.tags?.['name:th']);
  const withCuisine = elements.filter(e => e.tags?.cuisine);
  const byAmenity = {};
  for (const e of elements) {
    const a = e.tags?.amenity || '(none)';
    byAmenity[a] = (byAmenity[a] || 0) + 1;
  }
  console.log(`   nodes:        ${nodes.length}`);
  console.log(`   ways:         ${ways.length}`);
  console.log(`   with name:    ${withName.length} (${(withName.length / elements.length * 100).toFixed(1)}%)`);
  console.log(`   with name:th: ${withNameTh.length}`);
  console.log(`   with cuisine: ${withCuisine.length}`);
  console.log(`   by amenity:   ${JSON.stringify(byAmenity)}`);
  return { nodes: nodes.length, ways: ways.length, withName: withName.length };
}

async function main() {
  const args = parseArgs();
  if (args.help) { printHelp(); return; }
  if (args.list) { printList(); return; }

  console.log('🌍 ThiengKin · OSM Overpass Fetcher (M3.a)');
  console.log('━'.repeat(60));

  let bbox, name, label;
  if (args.bbox) {
    bbox = args.bbox;
    name = args.name;
    label = args.name;
  } else {
    const r = resolveBbox(args);
    bbox = r.bbox;
    name = r.name;
    label = r.label;
  }
  console.log(`📍 BBox:    s=${bbox.s}, w=${bbox.w}, n=${bbox.n}, e=${bbox.e}`);
  console.log(`🏷️  Label:   ${label}`);
  console.log(`📦 Output:  data/osm-${name}.json`);
  console.log();

  const query = buildQuery(bbox);
  console.log(`🔍 Overpass QL (${query.length} chars):`);
  console.log(query.split('\n').map(l => '   ' + l).join('\n'));
  console.log();

  console.log(`⏳ POST ${OVERPASS_ENDPOINTS[0]} (with ${OVERPASS_ENDPOINTS.length} endpoint fallbacks) ...`);
  const t0 = Date.now();
  let json;
  try {
    json = await fetchOverpass(query);
  } catch (e) {
    console.error(`\n💥 Fetch failed: ${e.message}`);
    console.error(`   Tip: Overpass rate limit = 10,000 req/day per endpoint. ลองรอสักครู่หรือเปลี่ยน endpoint`);
    process.exit(1);
  }
  const elapsed = ((Date.now() - t0) / 1000).toFixed(1);

  const elements = json.elements || [];
  console.log(`\n✅ Response in ${elapsed}s`);
  printStats(elements);

  // Save raw response
  const outDir = path.join(ROOT, 'data');
  fs.mkdirSync(outDir, { recursive: true });
  const outFile = path.join(outDir, `osm-${name}.json`);
  fs.writeFileSync(outFile, JSON.stringify(json, null, 2));
  const sizeKB = (fs.statSync(outFile).size / 1024).toFixed(1);
  console.log(`\n💾 Saved: ${outFile} (${sizeKB} KB)`);

  // Save a tiny summary alongside (for git diff sanity)
  const summary = {
    label,
    bbox,
    query: query,
    fetchedAt: new Date().toISOString(),
    elapsedSeconds: parseFloat(elapsed),
    counts: {
      total: elements.length,
      nodes: elements.filter(e => e.type === 'node').length,
      ways: elements.filter(e => e.type === 'way').length,
      withName: elements.filter(e => e.tags?.name).length,
    },
    source: 'OpenStreetMap Overpass API (https://overpass-api.de)',
    userAgent: USER_AGENT,
  };
  const summaryFile = path.join(outDir, `osm-${name}.meta.json`);
  fs.writeFileSync(summaryFile, JSON.stringify(summary, null, 2));
  console.log(`📋 Meta:   ${summaryFile}`);

  console.log(`\n🎯 Next: M3.b — parse with OsmImporter.kt + compare against FSQ 292 records`);
}

main().catch(err => {
  console.error('\n💥 Fatal:', err.message);
  if (err.stack) console.error(err.stack);
  process.exit(1);
});
