// merge-data.mjs
// เที่ยงกิน · Chiang Mai Final Data Merge
// รวม Foursquare + Manual seed → final dataset
// Input:  ../data/chiangmai-restaurants.json (Foursquare — ใช้ unfiltered เพราะ filter โดน rate limit)
//         ../data/chiangmai-restaurants-filtered.json (ถ้ามี — มี rating)
//         ../seed/chiangmai-manual.json
// Output: ../data/chiangmai-restaurants-final.json

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DATA_DIR = path.resolve(__dirname, '..', 'data');
const SEED_DIR = path.resolve(__dirname, '..', 'seed');

// ใช้ filtered ถ้ามี (มี rating), ไม่งั้น fallback ไป unfiltered
const FOURSQUARE_FILTERED = path.join(DATA_DIR, 'chiangmai-restaurants-filtered.json');
const FOURSQUARE_RAW = path.join(DATA_DIR, 'chiangmai-restaurants.json');
const MANUAL_FILE = path.join(SEED_DIR, 'chiangmai-manual.json');
const OUTPUT_FILE = path.join(DATA_DIR, 'chiangmai-restaurants-final.json');

function readIfExists(file, label) {
  if (!fs.existsSync(file)) {
    console.log(`⚠️  ${label} not found: ${file}`);
    return null;
  }
  return JSON.parse(fs.readFileSync(file, 'utf-8'));
}

function dedupeById(places) {
  const seen = new Map();
  for (const p of places) {
    // ใช้ id เป็น key — manual_* กับ fsq_* ไม่ชนกัน
    if (!seen.has(p.id)) {
      seen.set(p.id, p);
    } else {
      // ถ้าชน (เผื่อมี manual_xxx ที่ตรงกับ fsq_xxx) — merge
      const existing = seen.get(p.id);
      seen.set(p.id, { ...existing, ...p, source: 'merged' });
    }
  }
  return Array.from(seen.values());
}

async function main() {
  console.log('🍜 เที่ยงกิน · Chiang Mai Final Merge');
  console.log('━'.repeat(50));

  const foursquare = readIfExists(FOURSQUARE_FILTERED, 'Foursquare (filtered)')
                   || readIfExists(FOURSQUARE_RAW, 'Foursquare (raw)');
  const manual = readIfExists(MANUAL_FILE, 'Manual seed');

  if (!foursquare && !manual) {
    console.error('\n❌ ไม่พบ data source เลย');
    console.log('   รัน setup-chiangmai.mjs และ/หรือ filter-data.mjs ก่อน');
    process.exit(1);
  }

  const fsqPlaces = foursquare?.restaurants || [];
  const manualPlaces = manual?.restaurants || [];

  console.log(`📊 Foursquare (filtered): ${fsqPlaces.length} ร้าน`);
  console.log(`📊 Manual seed:         ${manualPlaces.length} ร้าน`);

  // mark source
  const fsqMarked = fsqPlaces.map(p => ({ ...p, source: p.source || 'foursquare' }));
  const manualMarked = manualPlaces.map(p => ({ ...p, source: 'manual' }));

  const merged = dedupeById([...fsqMarked, ...manualMarked]);

  // sort: manual first (curated), then by rating desc
  merged.sort((a, b) => {
    if (a.source === 'manual' && b.source !== 'manual') return -1;
    if (b.source === 'manual' && a.source !== 'manual') return 1;
    if (a.rating && b.rating) return b.rating - a.rating;
    return 0;
  });

  console.log(`📊 Merged total:         ${merged.length} ร้าน`);

  // Stats
  const bySource = merged.reduce((acc, p) => {
    acc[p.source] = (acc[p.source] || 0) + 1;
    return acc;
  }, {});
  console.log(`   └─ by source:`, bySource);

  const byCategory = merged.reduce((acc, p) => {
    const c = p.category_slug || 'other';
    acc[c] = (acc[c] || 0) + 1;
    return acc;
  }, {});
  console.log(`   └─ top categories:`, Object.entries(byCategory)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([k, v]) => `${k}(${v})`)
    .join(', ')
  );

  // Travel-mode features
  const travelHot = merged.filter(p => p.tags?.includes('highway_stop') || p.tags?.includes('travel'));
  const localFav = merged.filter(p => p.tags?.includes('local_favorite'));
  console.log(`   └─ Travel mode hot: ${travelHot.length}, Local favorites: ${localFav.length}`);

  // Output
  const output = {
    metadata: {
      city: 'Chiang Mai',
      created_at: new Date().toISOString(),
      foursquare_count: fsqPlaces.length,
      manual_count: manualPlaces.length,
      merged_count: merged.length,
      sources: ['foursquare', 'manual']
    },
    restaurants: merged
  };

  fs.writeFileSync(OUTPUT_FILE, JSON.stringify(output, null, 2));

  const sizeKB = (fs.statSync(OUTPUT_FILE).size / 1024).toFixed(1);
  console.log(`\n💾 Saved: ${path.relative(process.cwd(), OUTPUT_FILE)} (${sizeKB} KB)`);

  console.log('\n🎉 Next steps:');
  console.log('   - Import เข้า Android Room DB (Week 2)');
  console.log('   - Phase 1.5: เพิ่ม user votes, manual curation ต่อ');
}

main().catch(err => {
  console.error('\n💥 Fatal:', err.message);
  console.error(err.stack);
  process.exit(1);
});
