// filter-data.mjs
// เที่ยงกิน · Chiang Mai Restaurant Filter Script
// ดึง rating + review_count จาก Foursquare Place Details
// Filter: rating >= 4.0, review_count > 0
// Input:  ../data/chiangmai-restaurants.json
// Output: ../data/chiangmai-restaurants-filtered.json

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const FOURSQUARE_API_KEY = process.env.FOURSQUARE_API_KEY || 'YOUR_API_KEY_HERE';

const DATA_DIR = path.resolve(__dirname, '..', 'data');
const INPUT_FILE = path.join(DATA_DIR, 'chiangmai-restaurants.json');
const OUTPUT_FILE = path.join(DATA_DIR, 'chiangmai-restaurants-filtered.json');

// Config
const MIN_RATING = 4.0;
const MIN_REVIEW_COUNT = 1; // มีรีวิวอย่างน้อย 1
const DELAY_MS = 1100; // 1.1s between calls (Foursquare rate limit)

// Place Details fields ที่ต้องการ
const DETAIL_FIELDS = [
  'fsq_id',
  'name',
  'rating',
  'stats',
  'price',
  'hours',
  'tel',
  'website',
  'social_media',
  'photos',
  'tips'
].join(',');

async function fetchPlaceDetails(fsqId) {
  const url = `https://api.foursquare.com/v3/places/${fsqId}?fields=${DETAIL_FIELDS}`;
  const response = await fetch(url, {
    headers: {
      'Authorization': FOURSQUARE_API_KEY,
      'Accept': 'application/json'
    }
  });

  if (response.status === 401) {
    throw new Error('API key ไม่ถูกต้อง — เช็ค FOURSQUARE_API_KEY');
  }
  if (response.status === 404) {
    return null; // place ถูกลบ
  }
  if (response.status === 429) {
    throw new Error('Rate limit — รอ 60s แล้วลองใหม่');
  }
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Foursquare ${response.status}: ${text}`);
  }

  return response.json();
}

function mergePlaceData(basePlace, details) {
  if (!details) return null;

  return {
    ...basePlace,
    rating: details.rating ?? null,
    review_count: details.stats?.total_count ?? 0,
    price: details.price ?? basePlace.price ?? null,
    hours_popular: details.hours?.popular ?? basePlace.hours_popular ?? null,
    tel: details.tel ?? basePlace.tel ?? null,
    website: details.website ?? basePlace.website ?? null,
    social: {
      ...basePlace.social,
      ...(details.social_media || {})
    },
    photos_count: details.photos?.length ?? 0,
    tips_count: details.tips?.length ?? 0,
    detailed_at: new Date().toISOString()
  };
}

function passesFilter(place) {
  // rating ต้องมี + >= 4.0
  if (place.rating === null || place.rating === undefined) return false;
  if (place.rating < MIN_RATING) return false;

  // ต้องมีรีวิวอย่างน้อย 1
  if (!place.review_count || place.review_count < MIN_REVIEW_COUNT) return false;

  return true;
}

async function sleep(ms) {
  return new Promise(r => setTimeout(r, ms));
}

async function main() {
  console.log('🍜 เที่ยงกิน · Chiang Mai Filter');
  console.log('━'.repeat(50));
  console.log(`📥 Input:  ${path.relative(process.cwd(), INPUT_FILE)}`);
  console.log(`📤 Output: ${path.relative(process.cwd(), OUTPUT_FILE)}`);
  console.log(`⭐ Filter: rating >= ${MIN_RATING}, review_count >= ${MIN_REVIEW_COUNT}`);
  console.log();

  if (FOURSQUARE_API_KEY === 'YOUR_API_KEY_HERE') {
    console.error('❌ ไม่พบ FOURSQUARE_API_KEY');
    console.log('   $env:FOURSQUARE_API_KEY="your_key"');
    process.exit(1);
  }

  if (!fs.existsSync(INPUT_FILE)) {
    console.error(`❌ ไม่พบไฟล์: ${INPUT_FILE}`);
    console.log('   รัน: node setup-chiangmai.mjs ก่อน');
    process.exit(1);
  }

  const input = JSON.parse(fs.readFileSync(INPUT_FILE, 'utf-8'));
  const basePlaces = input.restaurants || [];

  if (basePlaces.length === 0) {
    console.error('❌ input file ว่างเปล่า');
    process.exit(1);
  }

  console.log(`📊 Found ${basePlaces.length} places to process\n`);

  const detailed = [];
  const skipped = [];
  const failed = [];

  for (let i = 0; i < basePlaces.length; i++) {
    const place = basePlaces[i];
    process.stdout.write(`\r   [${i + 1}/${basePlaces.length}] ${place.name.padEnd(30).slice(0, 30)}`);

    try {
      const details = await fetchPlaceDetails(place.id);
      const merged = mergePlaceData(place, details);

      if (!merged) {
        skipped.push(place.id);
      } else if (passesFilter(merged)) {
        detailed.push(merged);
      } else {
        skipped.push(place.id);
      }

      // rate limit (always sleep between calls, even on skip)
      if (i < basePlaces.length - 1) {
        await sleep(DELAY_MS);
      }
    } catch (error) {
      if (error.message.includes('Rate limit')) {
        console.log(`\n   ⏸️  Rate limit — รอ 60s...`);
        await sleep(60000);
        i--; // retry
        continue;
      }
      console.log(`\n   ⚠️  ${place.name}: ${error.message}`);
      failed.push({ id: place.id, name: place.name, error: error.message });
    }
  }

  console.log('\n\n' + '━'.repeat(50));
  console.log(`📊 สรุป:`);
  console.log(`   Input:    ${basePlaces.length}`);
  console.log(`   Passed:   ${detailed.length} (rating >= ${MIN_RATING} & has reviews)`);
  console.log(`   Skipped:  ${skipped.length} (low rating / no reviews / deleted)`);
  console.log(`   Failed:   ${failed.length} (API errors)`);

  if (detailed.length === 0) {
    console.error('\n❌ ไม่มีร้านผ่าน filter — เช็ค Foursquare coverage');
    process.exit(1);
  }

  // Sort by rating desc, then review_count desc
  detailed.sort((a, b) => {
    if (b.rating !== a.rating) return b.rating - a.rating;
    return b.review_count - a.review_count;
  });

  const output = {
    metadata: {
      ...input.metadata,
      filtered_at: new Date().toISOString(),
      filter_criteria: {
        min_rating: MIN_RATING,
        min_review_count: MIN_REVIEW_COUNT
      },
      input_count: basePlaces.length,
      passed_count: detailed.length,
      skipped_count: skipped.length,
      failed_count: failed.length
    },
    restaurants: detailed
  };

  fs.writeFileSync(OUTPUT_FILE, JSON.stringify(output, null, 2));

  const sizeKB = (fs.statSync(OUTPUT_FILE).size / 1024).toFixed(1);
  console.log(`\n💾 Saved: ${path.relative(process.cwd(), OUTPUT_FILE)} (${sizeKB} KB)`);

  // Top 5 preview
  console.log('\n🏆 Top 5:');
  detailed.slice(0, 5).forEach((p, i) => {
    console.log(`   ${i + 1}. ${p.name} — ★ ${p.rating} (${p.review_count} รีวิว)`);
  });

  console.log('\n🎉 Next steps:');
  console.log('   1. node merge-data.mjs  (Foursquare + manual seed)');
  console.log('   2. import เข้า Android Room DB');
}

main().catch(err => {
  console.error('\n💥 Fatal:', err.message);
  process.exit(1);
});
