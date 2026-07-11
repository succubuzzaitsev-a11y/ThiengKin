// setup-chiangmai.mjs
// เที่ยงกิน · Chiang Mai Restaurant Setup Script
// ดึงร้านอาหารในเชียงใหม่จาก Foursquare Places API
// Output: chiangmai-restaurants.json

const FOURSQUARE_API_KEY = process.env.FOURSQUARE_API_KEY || 'YOUR_API_KEY_HERE';

// Chiang Mai: Old City center
const CHIANG_MAI_CENTER = { lat: 18.7883, lng: 98.9853 };
const RADIUS_METERS = 25000; // 25 km — covers Old City + Hang Dong + Mae Rim + San Kamphaeng

// Foursquare food categories
// 13065 = Restaurant, 13032 = Café, 13003 = Bakery, 13034 = Coffee Shop
const FOOD_CATEGORIES = '13065,13032,13003,13034';

// Travel-mode hot categories
const TRAVEL_HOT_CATEGORIES = {
  13065: 'restaurant',
  13032: 'cafe',
  13003: 'bakery',
  13034: 'coffee',
  13002: 'thai',
  13004: 'noodle',
  13005: 'street_food'
};

async function searchFoursquare(offset = 0) {
  const params = new URLSearchParams({
    ll: `${CHIANG_MAI_CENTER.lat},${CHIANG_MAI_CENTER.lng}`,
    radius: RADIUS_METERS,
    categories: FOOD_CATEGORIES,
    limit: 50,
    offset: offset,
    sort: 'POPULARITY'
  });

  const url = `https://api.foursquare.com/v3/places/search?${params}`;
  console.log(`   GET ${url.replace(FOURSQUARE_API_KEY, 'KEY')}`);

  const response = await fetch(url, {
    headers: {
      'Authorization': FOURSQUARE_API_KEY,
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
    throw new Error(`Foursquare ${response.status}: ${text}`);
  }

  return response.json();
}

function transformPlace(place) {
  const categoryId = place.categories?.[0]?.id;
  const categoryName = place.categories?.[0]?.name || 'ร้านอาหาร';
  const categorySlug = TRAVEL_HOT_CATEGORIES[categoryId] || 'restaurant';

  return {
    id: place.fsq_id,
    name: place.name,
    name_th: place.name,
    category: categoryName,
    category_slug: categorySlug,
    lat: place.geocodes?.main?.latitude,
    lng: place.geocodes?.main?.longitude,
    address: place.location?.formatted_address || place.location?.address || '',
    district: place.location?.locality || '',
    province: place.location?.region || 'Chiang Mai',
    distance_m: place.distance || 0,
    tel: place.tel || null,
    website: place.website || null,
    social: place.social_media || {},
    hours: place.hours?.display || null,
    hours_popular: place.hours?.popular || null,
    rating: null,
    review_count: 0,
    price: place.price || null,
    fetched_at: new Date().toISOString()
  };
}

async function fetchAllPages() {
  const allPlaces = [];
  let offset = 0;
  const MAX_PAGES = 4;
  let totalEstimate = 0;

  for (let page = 0; page < MAX_PAGES; page++) {
    console.log(`\n📄 Page ${page + 1}/${MAX_PAGES} (offset ${offset})...`);

    try {
      const data = await searchFoursquare(offset);
      const places = data.results || [];
      totalEstimate = data.context?.total || totalEstimate;

      if (places.length === 0) {
        console.log('   ⏹️  No more results');
        break;
      }

      const transformed = places.map(transformPlace);
      allPlaces.push(...transformed);

      console.log(`   ✅ Got ${places.length} places`);

      if (places.length < 50) {
        console.log('   ⏹️  Last page');
        break;
      }

      offset += 50;
      await new Promise(r => setTimeout(r, 1000));
    } catch (error) {
      console.error(`   ❌ ${error.message}`);
      if (error.message.includes('Rate limit')) {
        console.log('   ⏸️  รอ 60s แล้วลองใหม่...');
        await new Promise(r => setTimeout(r, 60000));
        page--;
        continue;
      }
      break;
    }
  }

  return { allPlaces, totalEstimate };
}

async function main() {
  console.log('🍜 เที่ยงกิน · Chiang Mai Setup');
  console.log('━'.repeat(50));
  console.log(`📍 Center: ${CHIANG_MAI_CENTER.lat}, ${CHIANG_MAI_CENTER.lng} (Old City)`);
  console.log(`📏 Radius: ${RADIUS_METERS / 1000} km`);
  console.log(`🍽️  Categories: Restaurant, Café, Bakery, Coffee`);
  console.log();

  if (FOURSQUARE_API_KEY === 'YOUR_API_KEY_HERE') {
    console.error('❌ ไม่พบ FOURSQUARE_API_KEY\n');
    console.log('📝 วิธีตั้ง:');
    console.log('   1. สมัครฟรีที่ https://foursquare.com/products/places-api/');
    console.log('   2. สร้าง Project + API Key (100K calls/month ฟรี)');
    console.log('   3. รัน:');
    console.log('      $env:FOURSQUARE_API_KEY="your_key"  (PowerShell)');
    console.log('      export FOURSQUARE_API_KEY="your_key" (bash)');
    console.log('   4. node setup-chiangmai.mjs');
    process.exit(1);
  }

  const refresh = process.argv.includes('--refresh');
  if (refresh) {
    console.log('🔄 Refresh mode — ดึงข้อมูลใหม่ทั้งหมด\n');
  }

  const { allPlaces, totalEstimate } = await fetchAllPages();

  const unique = Array.from(new Map(allPlaces.map(p => [p.id, p])).values());

  console.log('\n' + '━'.repeat(50));
  console.log(`📊 สรุป:`);
  console.log(`   Total in Chiang Mai (Foursquare): ~${totalEstimate}`);
  console.log(`   Fetched: ${unique.length} unique restaurants`);

  if (unique.length < 50) {
    console.log(`\n⚠️  Warning: Foursquare coverage ในเชียงใหม์ค่อนข้างบาง (${unique.length} ร้าน)`);
    console.log('   แนะนำ: เสริมด้วย manual curation ใน Phase 1.5');
  }

  const output = {
    metadata: {
      city: 'Chiang Mai',
      center: CHIANG_MAI_CENTER,
      radius_m: RADIUS_METERS,
      categories: FOOD_CATEGORIES,
      source: 'Foursquare Places API v3',
      fetched_at: new Date().toISOString(),
      total_in_city_estimate: totalEstimate,
      fetched_count: unique.length
    },
    restaurants: unique
  };

  const fs = await import('fs');
  const filename = 'chiangmai-restaurants.json';
  fs.writeFileSync(filename, JSON.stringify(output, null, 2));

  const sizeKB = (fs.statSync(filename).size / 1024).toFixed(1);
  console.log(`\n💾 Saved: ${filename} (${sizeKB} KB)`);
  console.log('\n🎉 Next steps:');
  console.log('   1. node filter-data.mjs   (rating filter + cleanup)');
  console.log('   2. import เข้า Android Room DB');
  console.log('   3. เสริม manual curation (ร้านดังที่ Foursquare ไม่มี)');
  console.log('\n   💡 Tip: ดู preview:');
  console.log('      node -e "const d=require(\'./chiangmai-restaurants.json\'); console.log(d.restaurants.slice(0,3))"');
}

main().catch(err => {
  console.error('\n💥 Fatal:', err.message);
  process.exit(1);
});
