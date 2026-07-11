// setup-chiangmai.mjs
// เที่ยงกิน · Chiang Mai Restaurant Setup Script
// ดึงร้านอาหารในเชียงใหม่จาก Foursquare Places API (NEW: places-api.foursquare.com)
// Output: chiangmai-restaurants.json

const FOURSQUARE_API_KEY = process.env.FOURSQUARE_API_KEY || 'YOUR_API_KEY_HERE';

// Chiang Mai: Old City center
const CHIANG_MAI_CENTER = { lat: 18.7883, lng: 98.9853 };
const RADIUS_METERS = 25000; // 25 km

// New Foursquare Places API — May 2026
// IMPORTANT: `categories` param is IGNORED by new API → use `query` + RELEVANCE
const FOOD_QUERIES = ['restaurant', 'cafe', 'thai', 'noodle', 'coffee', 'street_food'];
const MAX_PAGES_PER_QUERY = 3; // 3 × 50 = 150 per query
const PER_PAGE = 50;

const API_BASE = 'https://places-api.foursquare.com';
const API_VERSION = '2025-06-17';

async function searchFoursquare(query, offset = 0) {
  const params = new URLSearchParams({
    ll: `${CHIANG_MAI_CENTER.lat},${CHIANG_MAI_CENTER.lng}`,
    radius: RADIUS_METERS,
    query: query,
    limit: PER_PAGE,
    offset: offset,
    sort: 'RELEVANCE'
  });

  const url = `${API_BASE}/places/search?${params}`;
  console.log(`   GET ${url.replace(FOURSQUARE_API_KEY, 'KEY').substring(0, 110)}...`);

  const response = await fetch(url, {
    headers: {
      'Authorization': `Bearer ${FOURSQUARE_API_KEY}`,
      'X-Places-Api-Version': API_VERSION,
      'Accept': 'application/json'
    }
  });

  if (response.status === 401) {
    throw new Error('API key ไม่ถูกต้อง — เช็ค FOURSQUARE_API_KEY');
  }
  if (response.status === 429) {
    throw new Error('Rate limit — รอ 1 นาทีแล้วลองใหม่');
  }
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Foursquare ${response.status}: ${text.substring(0, 200)}`);
  }

  return response.json();
}

// V2-style food categories mapping
const V2_CATEGORY_MAP = {
  'Asian Restaurant': 'restaurant',
  'Thai Restaurant': 'thai',
  'Noodle Restaurant': 'noodle',
  'Café': 'cafe',
  'Coffee Shop': 'coffee',
  'Bakery': 'bakery',
  'Breakfast Spot': 'cafe',
  'Street Food': 'street_food',
  'Som Tum Restaurant': 'thai',
  'Italian Restaurant': 'restaurant',
  'Vegan and Vegetarian Restaurant': 'restaurant',
  'Japanese Restaurant': 'restaurant',
  'Chinese Restaurant': 'restaurant',
  'Indian Restaurant': 'restaurant',
  'Korean Restaurant': 'restaurant',
  'Mexican Restaurant': 'restaurant',
  'American Restaurant': 'restaurant',
  'Seafood Restaurant': 'restaurant',
  'BBQ Joint': 'restaurant',
  'Pizza Place': 'restaurant',
  'Burger Joint': 'restaurant',
  'Food Court': 'restaurant',
  'Food Truck': 'street_food',
  'Snack Place': 'street_food',
  'Tea Room': 'cafe',
  'Dessert Shop': 'cafe',
  'Ice Cream Shop': 'cafe',
  'Juice Bar': 'cafe',
  'Bubble Tea Shop': 'cafe',
  'Pastry Shop': 'bakery',
  'Cafeteria': 'restaurant',
  'Diner': 'restaurant',
  'Steakhouse': 'restaurant',
  'Sushi Restaurant': 'restaurant',
  'Ramen Restaurant': 'noodle',
  'Dim Sum Restaurant': 'restaurant',
  'Hotpot Restaurant': 'restaurant',
};

function transformPlace(place) {
  const categoryName = place.categories?.[0]?.name || 'ร้านอาหาร';
  const categorySlug = V2_CATEGORY_MAP[categoryName] || 'restaurant';

  return {
    id: place.fsq_place_id,
    name: place.name,
    name_th: place.name,
    category: categoryName,
    category_slug: categorySlug,
    lat: place.latitude,
    lng: place.longitude,
    address: place.location?.formatted_address || place.location?.address || '',
    district: place.location?.locality || '',
    province: place.location?.region || 'Chiang Mai',
    distance_m: place.distance || 0,
    tel: place.tel || null,
    website: place.website || null,
    social: place.social_media || {},
    hours: place.hours?.display || null,
    rating: null,
    review_count: 0,
    price: place.price ?? null,
    fsq_url: place.link || null,
    fetched_at: new Date().toISOString()
  };
}

async function fetchAllPages() {
  const allPlaces = [];
  const seenIds = new Set();

  for (const query of FOOD_QUERIES) {
    console.log(`\n🔍 Query: "${query}"`);
    for (let page = 0; page < MAX_PAGES_PER_QUERY; page++) {
      const offset = page * PER_PAGE;
      try {
        const data = await searchFoursquare(query, offset);
        const places = data.results || [];

        if (places.length === 0) {
          console.log(`   ⏹️  No more results`);
          break;
        }

        let newCount = 0;
        for (const p of places) {
          if (p.fsq_place_id && !seenIds.has(p.fsq_place_id)) {
            seenIds.add(p.fsq_place_id);
            allPlaces.push(transformPlace(p));
            newCount++;
          }
        }

        console.log(`   ✅ Got ${places.length} (${newCount} new)`);

        if (places.length < PER_PAGE) break;
        await new Promise(r => setTimeout(r, 1100));
      } catch (error) {
        console.error(`   ❌ ${error.message}`);
        if (error.message.includes('Rate limit')) {
          await new Promise(r => setTimeout(r, 60000));
          page--;
          continue;
        }
        break;
      }
    }
  }

  return allPlaces;
}

async function main() {
  console.log('🍜 เที่ยงกิน · Chiang Mai Setup');
  console.log('━'.repeat(50));
  console.log(`📍 Center: ${CHIANG_MAI_CENTER.lat}, ${CHIANG_MAI_CENTER.lng} (Old City)`);
  console.log(`📏 Radius: ${RADIUS_METERS / 1000} km`);
  console.log(`🔍 Queries: ${FOOD_QUERIES.join(', ')}`);
  console.log(`📄 Max pages per query: ${MAX_PAGES_PER_QUERY} × ${PER_PAGE} = ${MAX_PAGES_PER_QUERY * PER_PAGE}`);
  console.log();

  if (FOURSQUARE_API_KEY === 'YOUR_API_KEY_HERE') {
    console.error('❌ ไม่พบ FOURSQUARE_API_KEY\n');
    console.log('📝 วิธีตั้ง:');
    console.log('   1. สมัครที่ https://foursquare.com/products/places-api/');
    console.log('   2. สร้าง Project + API Key');
    console.log('   3. รัน:');
    console.log('      $env:FOURSQUARE_API_KEY="your_key"');
    console.log('   4. node setup-chiangmai.mjs');
    process.exit(1);
  }

  const refresh = process.argv.includes('--refresh');
  if (refresh) console.log('🔄 Refresh mode\n');

  const allPlaces = await fetchAllPages();

  console.log('\n' + '━'.repeat(50));
  console.log(`📊 สรุป:`);
  console.log(`   Total unique: ${allPlaces.length}`);

  const byCat = {};
  allPlaces.forEach(p => { byCat[p.category_slug] = (byCat[p.category_slug] || 0) + 1; });
  console.log(`   By category:`, byCat);

  const output = {
    metadata: {
      city: 'Chiang Mai',
      center: CHIANG_MAI_CENTER,
      radius_m: RADIUS_METERS,
      queries: FOOD_QUERIES,
      source: 'Foursquare Places API v3 (places-api.foursquare.com)',
      fetched_at: new Date().toISOString(),
      fetched_count: allPlaces.length
    },
    restaurants: allPlaces
  };

  const fs = await import('fs');
  const filename = 'chiangmai-restaurants.json';
  fs.writeFileSync(filename, JSON.stringify(output, null, 2));

  const sizeKB = (fs.statSync(filename).size / 1024).toFixed(1);
  console.log(`\n💾 Saved: ${filename} (${sizeKB} KB)`);
  console.log('\n🎉 Next steps:');
  console.log('   1. node filter-data.mjs   (rating filter + cleanup)');
  console.log('   2. node merge-data.mjs    (merge with manual seed)');
  console.log('   3. import เข้า Android Room DB');
}

main().catch(err => {
  console.error('\n💥 Fatal:', err.message);
  process.exit(1);
});
