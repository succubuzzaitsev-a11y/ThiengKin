// osm-parse.mjs
// เที่ยงกิน · M3.b — OSM Overpass JSON → Restaurant[] parser
//
// Parse raw Overpass response (data/osm-<name>.json) → List<Restaurant>
// **MIRROR** ของ android/.../OsmImporter.kt — ถ้าแก้ logic ฝั่งหนึ่ง ต้อง sync อีกฝั่ง!
//
// Usage:
//   node scripts/osm-parse.mjs <name> --province <id> [--district <id>]
//   node scripts/osm-parse.mjs --all                       # parse all data/osm-*.json
//   node scripts/osm-parse.mjs --list                      # list available files
//
// Output:
//   data/parsed/osm-<name>.restaurants.json   (List<Restaurant>)
//   data/parsed/osm-<name>.restaurants.meta.json  (stats summary)
//
// Schema: matches Restaurant.kt (Android) field-for-field.
// Skipped: elements without name, without lat/lng, type=relation, type=area (not real geometry)

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '..');

const DATA_DIR = path.join(ROOT, 'data');
const PARSED_DIR = path.join(DATA_DIR, 'parsed');

// === Mirror of OsmImporter.kt ===

/** amenity → category (Thai label) */
const AMENITY_TO_CATEGORY = {
  restaurant: 'ร้านอาหาร',
  cafe: 'คาเฟ่',
  fast_food: 'ฟาสต์ฟู้ด',
  food_court: 'ศูนย์อาหาร',
};

/** Amenity keys ที่เก็บเป็น "key:value" (มี value หลายแบบ) */
const AMENITY_TAG_KEYS = [
  'cuisine',
  'wheelchair',
  'internet_access',
  'outdoor_seating',
  'air_conditioning',
  'smoking',
  'dog',
  'drive_through',
  'changing_table',
  'highchair',
  'kids_area',
  'bar',
  'payment:credit_card',
  'payment:cash',
  'payment:debit_card',
  'takeaway',
  'delivery',
  'diet:vegetarian',
  'diet:vegan',
  'diet:halal',
];

/** Boolean yes/no tags — เก็บเฉพาะ=yes (มี feature นั้น) */
const BOOLEAN_TAG_KEYS = [
  'wheelchair',
  'internet_access',
  'outdoor_seating',
  'air_conditioning',
  'takeaway',
  'delivery',
  'dog',
  'highchair',
  'kids_area',
  'drive_through',
  'changing_table',
  'bar',
  'payment:credit_card',
  'diet:vegetarian',
  'diet:vegan',
  'diet:halal',
];

// === Helpers ===

function getString(tags, key) {
  const v = tags?.[key];
  if (v == null) return null;
  if (typeof v !== 'string') return null;
  const s = v.trim();
  return s.length ? s : null;
}

/** name → name:th → name:en (priority) — match Kotlin getName() */
function getName(tags) {
  return getString(tags, 'name:th') || getString(tags, 'name') || getString(tags, 'name:en');
}

function buildAddress(tags) {
  const full = getString(tags, 'addr:full');
  if (full) return full;
  const parts = [];
  const hn = getString(tags, 'addr:housenumber');
  const st = getString(tags, 'addr:street');
  const sub = getString(tags, 'addr:suburb');
  const city = getString(tags, 'addr:city');
  if (hn) parts.push(hn);
  if (st) parts.push(st);
  if (sub) parts.push(`ต.${sub}`);
  if (city) parts.push(`อ.${city}`);
  return parts.length ? parts.join(' ') : null;
}

function buildTags(tags) {
  const out = [];
  const cuisine = getString(tags, 'cuisine');
  if (cuisine) out.push(`cuisine:${cuisine}`);
  for (const k of AMENITY_TAG_KEYS) {
    const v = getString(tags, k);
    if (v) out.push(`${k}:${v}`);
  }
  for (const k of BOOLEAN_TAG_KEYS) {
    const v = getString(tags, k);
    if (v === 'yes') out.push(k);
  }
  return out;
}

function parseElement(el, provinceId, districtId, nowMs) {
  if (el.type !== 'node' && el.type !== 'way') return null;
  const tags = el.tags || {};
  const name = getName(tags);
  if (!name) return null;          // skip unnamed

  // lat/lng — node: root | way: center (Overpass `out body` on way has center)
  const lat = el.lat ?? el.center?.lat;
  const lng = el.lon ?? el.center?.lon;
  if (lat == null || lng == null) return null;

  const amenity = getString(tags, 'amenity');
  const cuisine = getString(tags, 'cuisine');
  const category = AMENITY_TO_CATEGORY[amenity] ?? amenity ?? null;
  const categorySlug = (() => {
    if (!cuisine) return null;
    const first = cuisine.split(';')[0]?.trim();
    return first ? first : null;
  })();

  return {
    id: `osm_${el.id}`,
    name,
    nameTh: getString(tags, 'name:th'),
    category,
    categorySlug,
    lat,
    lng,
    address: buildAddress(tags),
    district: getString(tags, 'addr:city') || getString(tags, 'addr:suburb'),
    province: getString(tags, 'addr:province') || getString(tags, 'addr:state'),
    tel: getString(tags, 'contact:phone') || getString(tags, 'phone'),
    website: getString(tags, 'contact:website') || getString(tags, 'website'),
    rating: null,
    reviewCount: null,
    price: null,
    tags: buildTags(tags),
    source: 'osm',
    isFavorite: false,
    photoUrl: getString(tags, 'contact:image') || getString(tags, 'image'),
    menuText: null,
    aiSummary: null,
    provinceId,
    districtId: districtId ?? null,
    openingHours: getString(tags, 'opening_hours'),
    capacity: (() => {
      const c = getString(tags, 'capacity');
      if (!c) return null;
      const n = parseInt(c, 10);
      return Number.isFinite(n) ? n : null;
    })(),
    sourceUpdatedAt: nowMs,
  };
}

function parseFile(jsonPath, { provinceId, districtId, nowMs = Date.now() }) {
  const raw = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));
  const elements = raw.elements || [];
  const restaurants = [];
  const stats = {
    total: elements.length,
    parsed: 0,
    skipped: { unnamed: 0, noCoords: 0, relation: 0, area: 0 },
    byAmenity: {},
    withName: 0,
    withNameTh: 0,
    withCuisine: 0,
    withAddress: 0,
    withPhone: 0,
    withWebsite: 0,
    withHours: 0,
    withPhoto: 0,
  };

  for (const el of elements) {
    const type = el.type;
    if (type === 'relation') { stats.skipped.relation++; continue; }
    if (type === 'area') { stats.skipped.area++; continue; }
    if (type !== 'node' && type !== 'way') { stats.skipped.relation++; continue; }

    const tags = el.tags || {};
    const a = tags.amenity || '(none)';
    stats.byAmenity[a] = (stats.byAmenity[a] || 0) + 1;
    if (tags.name) stats.withName++;
    if (tags['name:th']) stats.withNameTh++;
    if (tags.cuisine) stats.withCuisine++;

    const r = parseElement(el, provinceId, districtId, nowMs);
    if (!r) {
      if (!getName(tags)) stats.skipped.unnamed++;
      else stats.skipped.noCoords++;
      continue;
    }
    stats.parsed++;
    if (r.address) stats.withAddress++;
    if (r.tel) stats.withPhone++;
    if (r.website) stats.withWebsite++;
    if (r.openingHours) stats.withHours++;
    if (r.photoUrl) stats.withPhoto++;
    restaurants.push(r);
  }

  return { restaurants, stats };
}

// === CLI ===

function parseArgs() {
  const args = process.argv.slice(2);
  if (args.includes('--help') || args.includes('-h')) return { help: true };
  if (args.includes('--list')) return { list: true };
  if (args.includes('--all')) return { all: true };

  let province = null;
  let district = null;
  const pIdx = args.indexOf('--province');
  if (pIdx >= 0 && args[pIdx + 1]) province = args[pIdx + 1];
  const dIdx = args.indexOf('--district');
  if (dIdx >= 0 && args[dIdx + 1]) district = args[dIdx + 1];

  const name = args.find(a => !a.startsWith('--')) || 'chiangmai-city';
  return { name, province, district };
}

function printHelp() {
  console.log(`🌍 ThiengKin · OSM Parser (M3.b)

Parse raw Overpass response (data/osm-<name>.json) → Restaurant[].
**MIRROR** ของ android/.../OsmImporter.kt — sync เมื่อแก้ field mapping.

Usage:
  node scripts/osm-parse.mjs <name> --province <id> [--district <id>]
  node scripts/osm-parse.mjs --all        # parse all data/osm-*.json
  node scripts/osm-parse.mjs --list       # list available osm-*.json files

Output:
  data/parsed/osm-<name>.restaurants.json       (List<Restaurant>)
  data/parsed/osm-<name>.restaurants.meta.json  (counts + skip reasons)

Schema: matches Restaurant.kt (Android) field-for-field.
`);
}

function listFiles() {
  if (!fs.existsSync(DATA_DIR)) { console.log('❌ No data/ directory'); return; }
  const files = fs.readdirSync(DATA_DIR)
    .filter(f => /^osm-.*\.json$/.test(f) && !f.endsWith('.meta.json'));
  if (files.length === 0) { console.log('❌ No data/osm-*.json files. Run osm-fetch first.'); return; }
  console.log('Available OSM files:');
  for (const f of files) {
    const stat = fs.statSync(path.join(DATA_DIR, f));
    const kb = (stat.size / 1024).toFixed(1);
    console.log(`  ${f.padEnd(36)} ${kb.padStart(8)} KB`);
  }
}

function deriveProvinceFromName(name) {
  // "chiang-mai-city" → "chiang_mai" (drop last "-city" if present, replace "-" → "_")
  let n = name;
  if (n.endsWith('-city')) n = n.slice(0, -'-city'.length);
  return n.replace(/-/g, '_');
}

function parseOne(name, opts) {
  const inFile = path.join(DATA_DIR, `osm-${name}.json`);
  if (!fs.existsSync(inFile)) {
    console.error(`❌ Not found: ${inFile}`);
    return null;
  }
  console.log(`\n📂 ${name}`);
  console.log(`   provinceId=${opts.provinceId}  districtId=${opts.districtId ?? '(none)'}`);

  const t0 = Date.now();
  const { restaurants, stats } = parseFile(inFile, opts);
  const elapsed = ((Date.now() - t0) / 1000).toFixed(2);

  fs.mkdirSync(PARSED_DIR, { recursive: true });
  const outFile = path.join(PARSED_DIR, `osm-${name}.restaurants.json`);
  const metaFile = path.join(PARSED_DIR, `osm-${name}.restaurants.meta.json`);
  fs.writeFileSync(outFile, JSON.stringify(restaurants, null, 2));
  fs.writeFileSync(metaFile, JSON.stringify({
    source: name,
    provinceId: opts.provinceId,
    districtId: opts.districtId ?? null,
    parsedAt: new Date().toISOString(),
    elapsedSeconds: parseFloat(elapsed),
    ...stats,
  }, null, 2));

  const sizeKB = (fs.statSync(outFile).size / 1024).toFixed(1);
  console.log(`   ✅ ${stats.parsed}/${stats.total} parsed (${elapsed}s, ${sizeKB} KB)`);
  console.log(`   📋 named=${stats.withName} th=${stats.withNameTh} cuisine=${stats.withCuisine} addr=${stats.withAddress} tel=${stats.withPhone} web=${stats.withWebsite} hours=${stats.withHours}`);
  console.log(`   🚫 skipped: ${JSON.stringify(stats.skipped)}`);
  console.log(`   🏷️  by amenity: ${JSON.stringify(stats.byAmenity)}`);
  console.log(`   💾 ${path.relative(ROOT, outFile)}`);
  console.log(`   📋 ${path.relative(ROOT, metaFile)}`);

  return { restaurants, stats, outFile, metaFile };
}

async function main() {
  const args = parseArgs();
  if (args.help) { printHelp(); return; }
  if (args.list) { listFiles(); return; }

  console.log('🌍 ThiengKin · OSM Parser (M3.b)');
  console.log('━'.repeat(60));

  const nowMs = Date.now();

  if (args.all) {
    const files = fs.readdirSync(DATA_DIR)
      .filter(f => /^osm-.*\.json$/.test(f) && !f.endsWith('.meta.json'));
    if (files.length === 0) {
      console.log('❌ No data/osm-*.json files. Run osm-fetch first.');
      return;
    }
    let totalParsed = 0, totalElements = 0;
    for (const f of files) {
      const name = f.replace(/^osm-/, '').replace(/\.json$/, '');
      const province = deriveProvinceFromName(name);
      // Heuristic: "-city" suffix means district = "city center" of that province
      let district = null;
      if (name.endsWith('-city')) district = `${province}_city`;
      const res = parseOne(name, { provinceId: province, districtId: district, nowMs });
      if (res) { totalParsed += res.stats.parsed; totalElements += res.stats.total; }
    }
    console.log(`\n🎯 Total: ${totalParsed}/${totalElements} restaurants parsed across ${files.length} files`);
    return;
  }

  if (!args.province) {
    console.error('❌ --province <id> required (or use --all)');
    console.error('   Example: node scripts/osm-parse.mjs mueang-nonthaburi --province nonthaburi --district mueang_nonthaburi');
    process.exit(1);
  }

  parseOne(args.name, {
    provinceId: args.province,
    districtId: args.district ?? null,
    nowMs,
  });
}

main().catch(err => {
  console.error('\n💥 Fatal:', err.message);
  if (err.stack) console.error(err.stack);
  process.exit(1);
});
