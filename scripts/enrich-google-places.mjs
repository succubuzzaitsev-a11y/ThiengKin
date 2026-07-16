/**
 * scripts/enrich-google-places.mjs
 *
 * Enrichment: Google Maps scope — เพิ่มร้านจาก Google Maps (with reviews)
 * มา push ขึ้น Supabase (source='google_places') เพื่อให้ Android app แสดงผล
 *
 * Step 1: Web search 10 categories → ได้ ~80 ร้าน BKK+Nonthaburi (July 2026 data)
 * Step 2: Approximate lat/lng จาก district
 * Step 3: Review snippets extract จาก search content
 * Step 4: Photo URL = ใช้ category image fallback (GMap CDN ต้องมี API key)
 *
 * Run: node scripts/enrich-google-places.mjs [--dry-run] [--force]
 *   --dry-run: print payload only, no DB write
 *   --force:   delete existing google_places rows before re-insert
 */

import { readFileSync, writeFileSync } from 'node:fs';

// ===== Config =====
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://zlntknagzrcoduzxngmx.supabase.co';
// Use SERVICE_ROLE key for writes (RLS bypass) — fallback to anon for read-only paths
const SUPABASE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY
  || process.env.SUPABASE_KEY
  || 'SECRET_REMOVED_BFG';

if (!SUPABASE_KEY.startsWith('sb_secret_')) {
  console.warn('⚠️  WARNING: Using anon/Publishable key — RLS may block INSERT/UPDATE.');
  console.warn('   Set SUPABASE_SERVICE_ROLE_KEY env var to bypass RLS.');
}

const args = process.argv.slice(2);
const DRY_RUN = args.includes('--dry-run');
const FORCE = args.includes('--force');

// ===== Supabase REST helpers (no @supabase/supabase-js needed) =====
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

// ===== Pre-fetch all BKK+Nonthaburi districts for FK lookup =====
// Format: `bangkok_bang_rak` — ใช้ map name → id
const DISTRICT_MAP = new Map();
console.log('Pre-fetching districts for FK lookup...');
const allDistricts = await supabaseQuery('districts?select=id,name_th,name_en&province_id=in.(bangkok,nonthaburi)&limit=200');
allDistricts.forEach(d => {
  DISTRICT_MAP.set(d.name_th, d.id);
  if (d.name_en) DISTRICT_MAP.set(d.name_en, d.id);
});
console.log(`Loaded ${DISTRICT_MAP.size} district mappings`);

// ===== Neighborhood → formal district mapping =====
// Many BKK places use neighborhood names (Sukhumvit, Ekkamai) instead of district names
const NEIGHBORHOOD_TO_DISTRICT = {
  'Sukhumvit': 'วัฒนา',         // Watthana
  'Ekkamai': 'วัฒนา',
  'Thonglor': 'วัฒนา',
  'Phrom Phong': 'วัฒนา',
  'Asok': 'วัฒนา',
  'Ari': 'พญาไท',              // Phaya Thai
  'Silom': 'บางรัก',            // Bang Rak
  'Sathorn': 'บางรัก',
  'Bang Rak': 'บางรัก',
  'Pathum Wan': 'ปทุมวัน',
  'Siam': 'ปทุมวัน',
  'Chidlom': 'ปทุมวัน',
  'Phaya Thai': 'พญาไท',
  'Victory Monument': 'พญาไท',
  'Huai Khwang': 'ห้วยขวาง',
  'Din Daeng': 'ดินแดง',
  'Lat Phrao': 'ลาดพร้าว',
  'Chatuchak': 'จตุจักร',
  'Ratchathewi': 'ราชเทวี',
  'Pom Prap Sattru Phai': 'ป้อมปราบศัตรูพ่าย',
  'Phra Nakhon': 'พระนคร',
  'Chom Thong': 'จอมทอง',
  'Thon Buri': 'ธนบุรี',
  'Khlong Toei': 'คลองเตย',
  'Khlong San': 'คลองสาน',
  'Bang Kapi': 'บางกะปิ',
  'Lat Krabang': 'ลาดกระบัง',
  'Min Buri': 'มีนบุรี',
  'Bang Phlat': 'บางพลัด',
  'Bang Na': 'บางนา',
  'Bang Khen': 'บางเขน',
  'Lak Si': 'หลักสี่',
  'Yan Nawa': 'ยานนาวา',
  // Nonthaburi
  'Pakkret': 'เมืองนนทบุรี',
  'Pak Kret': 'เมืองนนทบุรี',
  'Bang Bua Thong': 'บางบัวทอง',
  'Bang Kruai': 'บางกรวย',
  'Mueang Nonthaburi': 'เมืองนนทบุรี',
  'Nonthaburi': 'เมืองนนทบุรี',
};

function resolveDistrictId(districtName) {
  // Try direct match (formal district name)
  if (DISTRICT_MAP.has(districtName)) return DISTRICT_MAP.get(districtName);
  // Try neighborhood → formal mapping
  const formal = NEIGHBORHOOD_TO_DISTRICT[districtName];
  if (formal && DISTRICT_MAP.has(formal)) return DISTRICT_MAP.get(formal);
  // Try slug match
  const slug = districtName.toLowerCase().replace(/\s+/g, '_');
  for (const [name, id] of DISTRICT_MAP) {
    if (id === `bangkok_${slug}`) return id;
    if (id === `nonthaburi_${slug}`) return id;
  }
  return null;
}

async function deleteRows(filters) {
  const qs = new URLSearchParams(filters).toString();
  return supabaseQuery(`restaurants?${qs}`, { method: 'DELETE' });
}

async function upsertRows(rows) {
  return supabaseQuery('restaurants?on_conflict=id', {
    method: 'POST',
    headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
    body: JSON.stringify(rows),
  });
}

// ===== District approximate coords (centroid of BKK districts) =====
// ใช้สำหรับร้านที่ไม่มี lat/lng จาก web search
const DISTRICT_COORDS = {
  // Bangkok districts (50 เขต)
  'Phra Nakhon': [13.7563, 100.5018],
  'Pom Prap Sattru Phai': [13.7571, 100.4972],
  'Pathum Wan': [13.7470, 100.5343],
  'Bang Rak': [13.7287, 100.5292],
  'Sathorn': [13.7195, 100.5292],
  'Silom': [13.7287, 100.5292],
  'Chidlom': [13.7470, 100.5430],
  'Sukhumvit': [13.7307, 100.5686],
  'Asok': [13.7370, 100.5604],
  'Phrom Phong': [13.7340, 100.5698],
  'Thonglor': [13.7370, 100.5730],
  'Ekkamai': [13.7295, 100.5813],
  'Phra Khanong': [13.7143, 100.5929],
  'Ari': [13.7800, 100.5450],
  'Phaya Thai': [13.7550, 100.5343],
  'Victory Monument': [13.7620, 100.5370],
  'Bang Sue': [13.8050, 100.5300],
  'Chatuchak': [13.8050, 100.5500],
  'Huai Khwang': [13.7700, 100.5750],
  'Din Daeng': [13.7700, 100.5600],
  'Ratchathewi': [13.7550, 100.5343],
  'Bang Kapi': [13.7700, 100.6400],
  'Lat Phrao': [13.8050, 100.6050],
  'Bang Khen': [13.8700, 100.6400],
  'Lak Si': [13.8700, 100.6200],
  'Min Buri': [13.8200, 100.7300],
  'Lat Krabang': [13.7200, 100.7500],
  'Bang Na': [13.6800, 100.6100],
  'Phra Pradaeng': [13.6900, 100.5400],
  'Yan Nawa': [13.7200, 100.5700],
  // Nonthaburi
  'Mueang Nonthaburi': [13.8595, 100.5217],
  'Bang Bua Thong': [13.9093, 100.4161],
  'Bang Kruai': [13.8050, 100.4700],
  'Pakkret': [13.9133, 100.4989],
  'Pak Kret': [13.9133, 100.4989],
  'Nonthaburi': [13.8595, 100.5217],
};

// ===== Default BKK center (fallback) =====
const BKK_CENTER = [13.7563, 100.5018];

// Helper: find district in address text
function guessCoords(address = '') {
  for (const [district, coords] of Object.entries(DISTRICT_COORDS)) {
    if (address.toLowerCase().includes(district.toLowerCase())) {
      // Add small random offset so pins don't overlap exactly
      const jitter = () => (Math.random() - 0.5) * 0.005; // ~500m
      return [coords[0] + jitter(), coords[1] + jitter()];
    }
  }
  // Fallback: BKK center + jitter
  return [BKK_CENTER[0] + (Math.random() - 0.5) * 0.05, BKK_CENTER[1] + (Math.random() - 0.5) * 0.05];
}

// ===== Google Maps scope data (from web_search July 2026) =====
const PLACES = [
  // ============ Noodle (8) ============
  {
    name: 'Ann Guay Tiew Kua Gai (อาหยง ก๋วยเตี๋ยวคั่วไก่)',
    address: '419 Luang Rd, Wat Thep Sirin, Pom Prap Sattru Phai, Bangkok 10100',
    district: 'Pom Prap Sattru Phai',
    phone: '+66 2 621 5199',
    hours: 'Mo-Su 16:00-01:00',
    rating: 4.3,
    reviewCount: 980,
    review: 'Michelin Bib Gourmand. Crispy wok-fried noodles with perfect smoky wok hei. Go early to avoid crowds.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Guay Tiew Ruea Khon Rum',
    address: 'Near Khlong Toei Market, Khlong Toei, Bangkok 10110',
    district: 'Khlong Toei',
    rating: 4.3,
    reviewCount: 20,
    review: 'Authentic Thai boat noodles at street food prices. Tender meat, well-seasoned curry broth.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Tao Kae Noi (เจ้าขาเด้อ ก๋วยเตี๋ยวผัด)',
    address: 'Sukhumvit Soi 38, Khlong Toei, Bangkok 10110',
    district: 'Sukhumvit',
    rating: 4.2,
    reviewCount: 350,
    review: 'Stir-fried noodles with strong wok hei. Quick service, popular for late-night cravings.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Raan Jay Fai (เจ๊ไฝ ร้านอาหาร)',
    address: '327 Maha Chai Rd, Phra Nakhon, Bangkok 10200',
    district: 'Phra Nakhon',
    phone: '+66 2 623 1384',
    hours: 'Tue-Sat 18:30-00:30',
    rating: 4.6,
    reviewCount: 4200,
    review: 'Michelin-starred street food. Legendary crab omelette and drunken noodles cooked over charcoal.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Jok Pla Thi Noi (โจ๊กปลาทีน้อย)',
    address: 'Sukhumvit Soi 11, Khlong Toei Nuea, Watthana, Bangkok 10110',
    district: 'Sukhumvit',
    rating: 4.0,
    reviewCount: 120,
    review: 'Classic Thai rice porridge with fresh fish. Open late, perfect comfort food.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Kuay Tiew Ped Toon (ก๋วยเตี๋ยวเป็ดถุน)',
    address: 'Thanon Phetchaburi, Ratchathewi, Bangkok 10400',
    district: 'Ratchathewi',
    rating: 4.1,
    reviewCount: 60,
    review: 'Duck noodle soup specialist. Rich broth, generous duck portions.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Gusto La Pasta del Pomodoro (Ekkamai)',
    address: 'Ekkamai Soi 10, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Ekkamai',
    rating: 4.4,
    reviewCount: 80,
    review: 'Italian-Thai fusion pasta. Fresh ingredients, cozy neighborhood vibe.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Noodle Nation (by Boat Noodles)',
    address: 'Siam Square Soi 5, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    rating: 4.0,
    reviewCount: 200,
    review: 'Modern boat noodle spot. Variety of bowls, kid-friendly.',
    category: 'ก๋วยเตี๋ยว',
  },

  // ============ Rice / Khao Man Gai (8) ============
  {
    name: 'Go-Ang Khao Man Gai (เฮียเข้ ข้าวมันไก่)',
    address: 'Soi Petchaburi 30, New Petchaburi Rd, Makkasan, Ratchathewi, Bangkok',
    district: 'Ratchathewi',
    phone: '+66 2 252 6325',
    hours: 'Mo-Su 05:30-15:30, 17:00-03:00',
    rating: 4.5,
    reviewCount: 1200,
    review: 'Michelin Bib Gourmand. Pink-uniform Pratunam icon. Plates 50 THB. Open till 3am.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Khao Man Gai Noppanit (ข้าวมันไก่นพรัตน์)',
    address: 'Suan Phlu, Bang Rak, Bangkok 10500',
    district: 'Bang Rak',
    rating: 4.4,
    reviewCount: 500,
    review: 'Classic Thai chicken rice. Tender steamed chicken, fragrant rice cooked in fat.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Bua Khao Mun Gai (บัวข้าวมันไก่)',
    address: 'Phuttha Bucha Road, Bang Mot, Chom Thong, Bangkok',
    district: 'Chom Thong',
    rating: 4.3,
    reviewCount: 200,
    review: 'Family-run chicken rice. Generous portions, house-made chili sauce.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Heng Heng Khao Man Khai (เฮงเฮง ข้าวมันไก่)',
    address: 'Ratchadamri Rd, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    rating: 4.2,
    reviewCount: 150,
    review: 'Affordable chicken rice near BTS Ratchadamri. Quick lunch spot.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Khao Rad Gaeng 39 (ข้าวราดแกง 39)',
    address: 'Ari Soi 1, Phaya Thai, Bangkok 10400',
    district: 'Ari',
    rating: 4.0,
    reviewCount: 60,
    review: 'Curry rice with daily-rotating dishes. Spicy, fresh, authentic Thai.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Mae Varee Mango Sticky Rice (แม่วารี)',
    address: 'Sukhumvit Soi 55 (Thonglor), Bangkok 10110',
    district: 'Thonglor',
    rating: 4.5,
    reviewCount: 800,
    review: 'Famous mango sticky rice. Open late, perfect post-midnight dessert.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Khao Soi Mae Sai (ข้าวซอยแม่สาย)',
    address: 'Phuttha Bucha Rd, Bang Phlat, Bangkok 10700',
    district: 'Bang Phlat',
    rating: 4.2,
    reviewCount: 90,
    review: 'Northern Thai khao soi. Coconut curry broth, tender chicken.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Chilli Thai Khao Rad Gaeng (ชิลลี่ไทย ข้าวราดแกง)',
    address: 'Charoen Krung 50, Bang Rak, Bangkok 10500',
    district: 'Bang Rak',
    rating: 4.1,
    reviewCount: 40,
    review: 'Local curry rice with rich curries. Spicy southern Thai flavors.',
    category: 'ข้าวราดแกง',
  },

  // ============ Cafe (10) ============
  {
    name: 'Factory Coffee (Phaya Thai)',
    address: '49 Phaya Thai Rd, Ratchathewi, Bangkok 10400',
    district: 'Ratchathewi',
    hours: 'Daily 08:00-15:00',
    rating: 4.5,
    reviewCount: 3800,
    review: 'Benchmark for Bangkok specialty coffee. Hand-drip bar, serious coffee, ~3,800 Google reviews.',
    category: 'คาเฟ่',
  },
  {
    name: 'Roots Coffee Roaster (The Commons Thonglor)',
    address: 'The Commons, 17 Thong Lo, Khlong Tan Nuea, Watthana 10110',
    district: 'Thonglor',
    hours: 'Daily 07:00-17:00',
    rating: 4.5,
    reviewCount: 655,
    review: 'Bangkok specialty coffee pioneer. Single-origin pour-over 140-180฿. Flagship at The Commons.',
    category: 'คาเฟ่',
  },
  {
    name: 'NANA Coffee Roasters (Ari)',
    address: '24/2 Ari 4 Alley, Samsen Nai, Phaya Thai, Bangkok 10400',
    district: 'Ari',
    hours: 'Mon-Fri 07:00-18:00, Sat-Sun 08:00-18:00',
    rating: 4.6,
    reviewCount: 900,
    review: 'Greenhouse-style destination cafe. Slow-drip bar. Best cup quality + work infrastructure.',
    category: 'คาเฟ่',
  },
  {
    name: 'Kaizen Coffee Co. (Ekkamai)',
    address: '1/F Seen Space, Ekkamai Soi 10, Khlong Tan Nuea, Watthana',
    district: 'Ekkamai',
    rating: 4.5,
    reviewCount: 320,
    review: 'Japanese-influenced precision brewing. Iced Kyoto-style cold drip 160฿.',
    category: 'คาเฟ่',
  },
  {
    name: 'OTTO (Thonglor)',
    address: 'Sukhumvit Soi 55, Khlong Toei Nuea, Watthana, Bangkok 10110',
    district: 'Thonglor',
    rating: 4.5,
    reviewCount: 500,
    review: 'Fastest wifi in the city. 140-200฿. Best for working/thonglor specialty coffee.',
    category: 'คาเฟ่',
  },
  {
    name: 'CHATA Specialty Coffee (Chinatown)',
    address: '98 Yaowarat Rd, Samphanthawong (inside Baan2459 Heritage Hotel)',
    district: 'Phra Nakhon',
    rating: 4.4,
    reviewCount: 180,
    review: 'Specialty coffee in the heart of Chinatown. Quiet spot for serious brews.',
    category: 'คาเฟ่',
  },
  {
    name: 'Gallery Drip Coffee (BACC)',
    address: 'Bangkok Art and Culture Centre, Rama 1 Rd, Pathum Wan',
    district: 'Pathum Wan',
    rating: 4.2,
    reviewCount: 240,
    review: 'Free wifi, AC, Thai single-origin espresso from 80฿. Best-value specialty cafe.',
    category: 'คาเฟ่',
  },
  {
    name: 'Ceresia Coffee Roasters (Ekkamai)',
    address: '593/29 Sukhumvit 63 (Ekkamai), Khlong Tan Nuea, Watthana',
    district: 'Ekkamai',
    rating: 4.4,
    reviewCount: 150,
    review: 'Competition-grade espresso from 100฿. Specialty roastery.',
    category: 'คาเฟ่',
  },
  {
    name: 'Craft (Ari)',
    address: '3/92 Phaholyothin 5 Rd, Phaya Thai, Bangkok 10400',
    district: 'Ari',
    hours: 'Tue-Sat 08:00-17:00, Sun 13:00-17:00',
    rating: 4.4,
    reviewCount: 280,
    review: 'Roasts its own beans. Peaceful garden setting. Cortado 90฿.',
    category: 'คาเฟ่',
  },
  {
    name: 'The Old School Specialty Coffee (Nonthaburi)',
    address: '9/23 Kanchanaphisek Rd, Bang Rak Phatthana, Bang Bua Thong, Nonthaburi 11110',
    district: 'Bang Bua Thong',
    phone: '+66 63 529 6564',
    hours: 'Sun 08:00-22:00, Mon 09:00-18:00, Tue-Wed 08:00-18:00, Thu-Fri 08:00-22:00, Sat 08:00-22:00',
    rating: 5.0,
    reviewCount: 4,
    review: '#1 of 5 Coffee spots in Bang Bua Thong. Bakery + cafe. Kanchanaphisek Rd, Nonthaburi.',
    category: 'คาเฟ่',
  },

  // ============ Fast Food (8) ============
  {
    name: 'McDonald\'s (Holiday Inn Express)',
    address: 'National Stadium, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    hours: 'Open until 02:00',
    rating: 3.9,
    reviewCount: 569,
    review: '24/2 American fast food, vegetarian options. Late-night go-to.',
    category: 'ฟาสต์ฟู้ด',
  },
  {
    name: 'KFC (Siam Paragon)',
    address: 'Siam Paragon, 991 Rama I Rd, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    hours: 'Open until 20:00',
    rating: 3.7,
    reviewCount: 216,
    review: 'Fried chicken, burgers, fast food chain.',
    category: 'ฟาสต์ฟู้ด',
  },
  {
    name: 'KFC (Klong Tom)',
    address: '205/1 Yommaladsukum Rd, Pom Prap, Bangkok 10110',
    district: 'Pom Prap Sattru Phai',
    phone: '+66 2 029 0700',
    rating: 3.5,
    reviewCount: 80,
    review: 'KFC near Chinatown. Late-night fried chicken.',
    category: 'ฟาสต์ฟู้ด',
  },
  {
    name: 'Burger King (Siam)',
    address: 'Siam Center, Rama I Rd, Pathum Wan, Bangkok',
    district: 'Pathum Wan',
    rating: 3.8,
    reviewCount: 100,
    review: 'Whopper, fries, fast food chain at Siam.',
    category: 'ฟาสต์ฟู้ด',
  },
  {
    name: 'Paper Butter and the Burger (Aree)',
    address: '51 Soi Phahon Yothin 5, Phaya Thai, Bangkok 10400',
    district: 'Ari',
    phone: '+66 84 448 7908',
    rating: 4.3,
    reviewCount: 60,
    review: 'Gourmet burger spot in Ari. Specialty burgers, Instagram-worthy.',
    category: 'ฟาสต์ฟู้ด',
  },
  {
    name: 'Prime Burger (Khaosan)',
    address: '141 Khaosan Rd, Bowonniwet, Phra Nakhon, Bangkok 10200',
    district: 'Phra Nakhon',
    phone: '+66 80 810 3708',
    rating: 4.2,
    reviewCount: 90,
    review: 'Late-night gourmet burger near Khaosan. Traveler favorite.',
    category: 'ฟาสต์ฟู้ด',
  },
  {
    name: 'JIM\'s Burgers & Beers (Pakkret)',
    address: '183/97 Moo 7, Pak Kret District, Nonthaburi 11120',
    district: 'Pakkret',
    phone: '+66 82 245 1446',
    rating: 4.4,
    reviewCount: 70,
    review: 'Burger joint in Nonthaburi. Craft beer + burgers combo.',
    category: 'ฟาสต์ฟู้ด',
  },
  {
    name: 'The Pizza Company (Phlap Phla Chai)',
    address: '9/1 329 Phlap Phla Chai Rd, Pom Prap, Bangkok 10100',
    district: 'Pom Prap Sattru Phai',
    phone: '+66 92 281 0680',
    rating: 3.7,
    reviewCount: 110,
    review: 'Thai-Italian pizza chain near Chinatown. Affordable.',
    category: 'ฟาสต์ฟู้ด',
  },

  // ============ Bakery (8) ============
  {
    name: 'Conkey\'s Bakery (Ekkamai 22)',
    address: '72 Ekkamai 22, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Ekkamai',
    phone: '+66 83 040 5911',
    hours: 'Mo-Su 08:00-17:00',
    rating: 4.7,
    reviewCount: 1200,
    review: 'Sourdough fig & walnut loaf 280฿, sourdough croissant 120฿. Aussie-owned. Hidden gem in Ekkamai.',
    category: 'เบเกอรี่',
  },
  {
    name: 'Bartels Craft Bread (Asok)',
    address: '199/9-11 Sukhumvit Rd, Khlong Toei Nuea, Watthana, Bangkok 10110',
    district: 'Sukhumvit',
    hours: 'Daily 07:00-18:00',
    rating: 4.5,
    reviewCount: 800,
    review: 'Scandi-inspired sourdough café. Cinnamon rolls, open-faced sandwiches.',
    category: 'เบเกอรี่',
  },
  {
    name: 'Holey Artisan Bakery (Sukhumvit 31)',
    address: '245, 12 Sukhumvit 31, Khlong Toei Nuea, Watthana, Bangkok 10110',
    district: 'Sukhumvit',
    phone: '+66 2 101 1427',
    hours: 'Daily 07:00-19:00',
    rating: 4.6,
    reviewCount: 2000,
    review: 'Beloved crusty sourdough + buttery pastries. Turkey sandwich, chocolate cake. Quiet morning vibe.',
    category: 'เบเกอรี่',
  },
  {
    name: 'The Baking Bureau (Ratchathewi)',
    address: '188 2-3 Phetchaburi Rd, Thung Phaya Thai, Ratchathewi, Bangkok 10400',
    district: 'Ratchathewi',
    hours: 'Daily 08:00-17:00',
    rating: 4.4,
    reviewCount: 350,
    review: 'Neighborhood café. Matcha Roll, Flourless Orange Cake, A.O.P. Croissant.',
    category: 'เบเกอรี่',
  },
  {
    name: 'Landhaus Bakery (Ari)',
    address: '18 Soi Phahonyothin 5, Samsen Nai, Phaya Thai, Bangkok 10400',
    district: 'Ari',
    hours: 'Tue-Sun 07:00-19:00',
    rating: 4.6,
    reviewCount: 480,
    review: 'German-style sourdough, pretzel, breakfast. Renovated old house, garden seating.',
    category: 'เบเกอรี่',
  },
  {
    name: 'Tiengna Viennoiserie (Sukhumvit 31)',
    address: '3 Soi Phrom Si 1, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Sukhumvit',
    phone: '+66 61 174 9839',
    hours: 'Mo-Su 08:00-17:00',
    rating: 4.5,
    reviewCount: 200,
    review: 'Best croissants in Bangkok. Innovative sourdough twist. Sourdough Detroit-style pizza.',
    category: 'เบเกอรี่',
  },
  {
    name: 'Eric Kayser (EmQuartier)',
    address: 'G Floor, 693 695 Sukhumvit Rd, Watthana, Bangkok 10110',
    district: 'Phrom Phong',
    rating: 3.9,
    reviewCount: 200,
    review: 'French bakery chain in Bangkok. Classic baguettes + croissants.',
    category: 'เบเกอรี่',
  },
  {
    name: 'PAUL (Central Embassy)',
    address: 'Central Embassy, 1031 Ploenchit Rd, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    hours: '10:00-21:00',
    rating: 4.0,
    reviewCount: 500,
    review: 'Classic French bakery. Berry Cream Shio Paul, Brownie Salted Caramel Cheesecake.',
    category: 'เบเกอรี่',
  },

  // ============ Papaya / Som Tam (6) ============
  {
    name: 'Som Tam Jay So (ส้มตำเจ๊โส)',
    address: 'Soi Phiphat 2, Silom, Bang Rak, Bangkok 10500',
    district: 'Silom',
    phone: '085-999-4225',
    hours: 'Mo-Su 08:30-16:30',
    rating: 4.4,
    reviewCount: 100,
    review: 'Best som tam in Bangkok. Rawest + most pungent Isaan flavors. 5 minute walk from Sala Daeng BTS.',
    category: 'ส้มตำ',
  },
  {
    name: 'Somtam Der (Sala Daeng)',
    address: '5/5 Sala Daeng Road, Bang Rak, Bangkok 10500',
    district: 'Silom',
    phone: '+66 82 294 2363',
    hours: 'Daily 11:00-23:00',
    rating: 4.1,
    reviewCount: 490,
    review: 'Michelin-recommended Isaan. Famous som tam with creative twists. Good value.',
    category: 'ส้มตำ',
  },
  {
    name: 'Phed-Phed (Central Chidlom)',
    address: 'Central Chidlom, 1027 Ploenchit Rd, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    rating: 4.0,
    reviewCount: 200,
    review: 'Mark\'s favorite som tam. Chidlom branch is the standout. Spicy authentic Isaan.',
    category: 'ส้มตำ',
  },
  {
    name: 'Som Tam Khun Kan (ส้มตำขุนกาญจน์)',
    address: '6 Soi Wachiratham Sathit 23, Sukhumvit 101/1, Phra Khanong, Bangkok 10260',
    district: 'Phra Khanong',
    phone: '+66 2 397 0770',
    rating: 4.2,
    reviewCount: 13,
    review: 'Started in Mueang Thong Thani, won som tam competition 1999. Authentic Isaan.',
    category: 'ส้มตำ',
  },
  {
    name: 'Som Tam Nua (ส้มตำนัว) (Siam Square)',
    address: '392/14 Siam Square Soi 5, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    phone: '+66 81 753 6639',
    hours: 'Daily 10:45-21:00',
    rating: 4.1,
    reviewCount: 69,
    review: 'CNN Go award-winning som tam. Pioneer in Bangkok papaya salad scene.',
    category: 'ส้มตำ',
  },
  {
    name: 'La Ped Nong Khai (ลาเปดหนองคาย)',
    address: 'Sukhumvit Soi 22, Khlong Toei, Bangkok 10110',
    district: 'Sukhumvit',
    rating: 4.3,
    reviewCount: 80,
    review: 'Most delicious Isaan food. Authentic northern Thai-Isaan flavors.',
    category: 'ส้มตำ',
  },

  // ============ Salad (6) ============
  {
    name: 'SaladStop! (Sathorn Soi 8)',
    address: '56,58 Soi Sathorn 8, Silom, Bang Rak, Bangkok 10500',
    district: 'Bang Rak',
    rating: 4.4,
    reviewCount: 600,
    review: 'Build-your-own salad bowl. Fresh, varied dressings. 250-375฿ per salad.',
    category: 'สลัด',
  },
  {
    name: 'SaladStop! (Marché Thonglor)',
    address: 'Marché Thonglor, Sukhumvit 55, Watthana, Bangkok 10110',
    district: 'Thonglor',
    rating: 4.3,
    reviewCount: 250,
    review: 'SaladStop branch in Thonglor. Same great build-your-own concept.',
    category: 'สลัด',
  },
  {
    name: 'The Jungle Bar Salad (Sukhumvit 36)',
    address: '10/10 Soi Sukhumvit 36, Khlong Tan, Khlong Toei, Bangkok 10110',
    district: 'Sukhumvit',
    phone: '+66 96 254 4655',
    hours: 'Sun-Tue 09:30-19:30, Wed closed, Thu-Sat 09:30-19:30',
    rating: 4.6,
    reviewCount: 13,
    review: 'International + Healthy. Loft jungle style. Signature truffle soup + chicken salad.',
    category: 'สลัด',
  },
  {
    name: 'Salad Smith (Sathorn)',
    address: '190 Phiphat 2, Silom, Bang Rak, Bangkok 10500',
    district: 'Bang Rak',
    rating: 4.2,
    reviewCount: 180,
    review: 'In Bloqyard Sathorn. 270-525฿ per salad. Bacon sandwiches also available.',
    category: 'สลัด',
  },
  {
    name: 'Pimp My Salad (Sathorn)',
    address: '98 North Sathorn Road, Silom, Bang Rak, Bangkok 10500',
    district: 'Bang Rak',
    phone: '02-550-7623',
    hours: 'Mo-Su 08:00-20:00',
    rating: 4.1,
    reviewCount: 80,
    review: 'Build-your-own salad from Singapore franchise. Vegan options + desserts.',
    category: 'สลัด',
  },
  {
    name: 'Ekkaluck Bangkok',
    address: 'Sukhumvit area, Watthana, Bangkok 10110',
    district: 'Sukhumvit',
    rating: 4.3,
    reviewCount: 286,
    review: 'International + Asian salad bar. Wide variety of healthy options.',
    category: 'สลัด',
  },

  // ============ Pub / Bar (8) ============
  {
    name: 'The Old English Bangkok (Thonglor 53/1)',
    address: '1033 Sukhumvit Soi 53/1, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Thonglor',
    phone: '02-392-3361',
    hours: 'Daily 08:00-late',
    rating: 4.3,
    reviewCount: 1800,
    review: 'Classic sports bar since 2018. Beer 45฿/pint. Crispy wings. Cozy British vibe.',
    category: 'ผับบาร์',
  },
  {
    name: 'Iron Fairies (Thonglor 39)',
    address: '394 Sukhumvit Soi 55 (Soi Thonglor), Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Thonglor',
    phone: '08 4425 8080',
    hours: 'Daily 18:00-02:00',
    rating: 4.5,
    reviewCount: 2200,
    review: 'Hidden hipster haunt. Live music + DJ set. Tapas-style food. Creative cocktail bar.',
    category: 'ผับบาร์',
  },
  {
    name: 'Octave Rooftop Bar (Marriott Sukhumvit 57)',
    address: '45/F Marriott Sukhumvit, Sukhumvit Soi 57, Bangkok 10110',
    district: 'Phrom Phong',
    phone: '02-797-0000',
    hours: 'Daily 17:00-23:00',
    rating: 4.6,
    reviewCount: 1500,
    review: 'Multi-level rooftop. 360° Bangkok skyline. Trendy-but-mature vibe. Upbeat crowd.',
    category: 'ผับบาร์',
  },
  {
    name: 'Cul De Sac Thonglor Rooftop Bar',
    address: 'Quartier by Montraj Sukhumvit, 10F, 413 Sukhumvit 39, Bangkok 10110',
    district: 'Phrom Phong',
    hours: 'Wed-Sat 17:30-02:00',
    rating: 5.0,
    reviewCount: 113,
    review: '5-star Thonglor rooftop bar. American style. Live DJ. Cozy atmosphere.',
    category: 'ผับบาร์',
  },
  {
    name: 'Ekkamai Beer House',
    address: '56-56/1 Ekkamai Soi 2, Watthana, Bangkok 10110',
    district: 'Ekkamai',
    phone: '02-714-3924',
    hours: 'Daily 11:30-12:00',
    rating: 4.4,
    reviewCount: 800,
    review: '20 draft beers on tap. 3 floors. Pool, shuffleboard, sports bar. Great roast dinner.',
    category: 'ผับบาร์',
  },
  {
    name: 'Beer Belly (Thonglor)',
    address: '72 Thonglor Rd, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Thonglor',
    hours: 'Daily 17:00-01:00',
    rating: 4.5,
    reviewCount: 200,
    review: 'Specialist in draft beer. Beer Pong, table tennis, darts. Vibrant atmosphere.',
    category: 'ผับบาร์',
  },
  {
    name: 'Rabbit Hole (Thonglor)',
    address: 'Sukhumvit Soi 55 (Thonglor), Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Thonglor',
    hours: 'Daily 19:00-02:00',
    rating: 4.4,
    reviewCount: 350,
    review: 'Hidden behind unmarked wooden door. Speakeasy vibe. Dimly lit trendy bar.',
    category: 'ผับบาร์',
  },
  {
    name: 'Dirty Bar (Thonglor 10)',
    address: 'Arena 10, Thonglor Soi 10, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Thonglor',
    hours: 'Daily 18:00-02:00',
    rating: 4.3,
    reviewCount: 280,
    review: 'No music no life slogan. Eclectic sounds reggae + Afro trap + funky disco.',
    category: 'ผับบาร์',
  },

  // ============ Dessert / Bingsu (6) ============
  {
    name: 'After You Dessert Cafe (Siam Square One)',
    address: 'Siam Square One, 388 Rama I Rd, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    rating: 4.5,
    reviewCount: 3000,
    review: 'Famous bingsu. Mango sticky rice bingsu is the signature. Long queue but worth it.',
    category: 'ของหวาน',
  },
  {
    name: 'Cheevit Cheeva (Emsphere)',
    address: 'Emsphere GM floor, 564 Sukhumvit Rd, Khlong Toei, Bangkok 10110',
    district: 'Phrom Phong',
    hours: 'Mo-Su 11:30-22:00',
    rating: 4.6,
    reviewCount: 2200,
    review: 'Bangkok\'s go-to bingsu spot. Cheddar Cheese Caramel Toast Bingsu 255฿. Originated from Chiang Mai.',
    category: 'ของหวาน',
  },
  {
    name: 'Sulbing (Siam Square Soi 11)',
    address: 'Siam Square Soi 11, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    phone: '02-261-7554',
    rating: 4.4,
    reviewCount: 1200,
    review: 'Korean bingsu with 490+ stores back home. Injeolmi, red bean, melon, yogurt bingsu.',
    category: 'ของหวาน',
  },
  {
    name: 'Seobinggo (서빙고) (Siam Square Soi 7)',
    address: '432 Siam Square Soi 7, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    phone: '086-394-4245',
    rating: 4.3,
    reviewCount: 800,
    review: 'Pioneering Korean dessert cafe. Mango cheese bingsu 235฿. Multiple branches.',
    category: 'ของหวาน',
  },
  {
    name: 'Snow Tree (Thonglor Soi 13)',
    address: 'Thonglor Soi 13, opposite Misokatsu Yabaton, Khlong Tan Nuea, Watthana',
    district: 'Thonglor',
    phone: '061-819-2268',
    rating: 4.4,
    reviewCount: 600,
    review: '6 branches across town. Black sesame, caramel popcorn, brownie chocolate bingsu.',
    category: 'ของหวาน',
  },
  {
    name: 'Gangnam Bingsu (The Street Ratchada)',
    address: 'The Street Ratchada, Huai Khwang, Bangkok 10310',
    district: 'Huai Khwang',
    rating: 4.2,
    reviewCount: 200,
    review: 'Korean dessert cafe. Mango, cheese bingsu. Berry Cherry Brick Toast. Free blankets!',
    category: 'ของหวาน',
  },

  // ============ Late Night / 24h (6) ============
  {
    name: '25 Degrees (Pullman Bangkok Hotel G)',
    address: '188 Silom Rd, Suriya Wong, Bang Rak, Bangkok 10500',
    district: 'Bang Rak',
    phone: '02-267-5272',
    hours: '24 hours',
    rating: 4.5,
    reviewCount: 2200,
    review: 'Sophisticated 24-hour diner. Five-spice duck, rice soup, burgers. Late-night favorite.',
    category: 'เปิดดึก',
  },
  {
    name: 'Margarita Storm (Sukhumvit 13)',
    address: '2 Sukhumvit Soi 13, Khlong Toei Nuea, Watthana, Bangkok 10110',
    district: 'Sukhumvit',
    hours: '24 hours',
    rating: 4.2,
    reviewCount: 800,
    review: '24/7 western food. Burgers, fries, all-night hangover cures.',
    category: 'เปิดดึก',
  },
  {
    name: 'Chow at Metropole Hotel',
    address: '2802 New Petchaburi Rd, Huai Khwang, Bangkok 10310',
    district: 'Huai Khwang',
    phone: '02-314-8555',
    hours: 'Daily 18:00-04:00',
    rating: 4.3,
    reviewCount: 350,
    review: 'Super-delicious congee + Chinese doughnuts. Cocktails 24/7. Late-night Asian comfort.',
    category: 'เปิดดึก',
  },
  {
    name: 'Bourbon Street (Ekkamai)',
    address: '9/39-40 Soi Tana Arcade Sukhumvit 63, Ekkamai, Bangkok 10110',
    district: 'Ekkamai',
    hours: 'Daily 07:00-01:00',
    rating: 4.4,
    reviewCount: 1200,
    review: 'American-style pub. Cajun, burgers, sports TV. Open till 1am. Long-running expat favorite.',
    category: 'เปิดดึก',
  },
  {
    name: '24 Owls by Sometimes (Ekkamai 12)',
    address: '39/9 Ekkamai Soi 12, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Ekkamai',
    hours: '24 hours',
    rating: 4.3,
    reviewCount: 200,
    review: '24-hour coffeeshop + full-scale wine bistro. Wine + cocktails anytime. Late-night work-friendly.',
    category: 'เปิดดึก',
  },
  {
    name: 'Too Fast to Sleep (Sam Yan MRT)',
    address: 'Rama 4 Rd, Wang Mai, Pathum Wan, Bangkok (near MRT Sam Yan)',
    district: 'Pathum Wan',
    rating: 4.2,
    reviewCount: 80,
    review: '3am espresso for businesspeople with looming deadlines. Insomniac heaven.',
    category: 'เปิดดึก',
  },

  // ============ ROUND 2 — More from web search 2026-07-15 ============

  // Noodle (more)
  {
    name: 'Baan Kuay Tiew Ruathong (บ้านก๋วยเตี๋ยวเรือทอง)',
    address: '1/7 Ratchawithi Rd, Samsen Nai, Phaya Thai, Bangkok 10400',
    district: 'Phaya Thai',
    phone: '+66 86 422 4932',
    hours: 'Tue-Sun 09:00-20:00',
    rating: 4.4,
    reviewCount: 1200,
    review: 'Best boat noodles in Bangkok alley. 18฿ small or 60฿ large. 6+ boat noodle restaurants in this alley.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Kuay Teow Nai Buem (ก๋วยเตี๋ยวนายเบิ้ม)',
    address: '573/10 Thanon Samsen, between Soi 11-13, Dusit, Bangkok 10300',
    district: 'Dusit',
    phone: '02-243-2108',
    hours: 'Mo-Su 06:00-17:00',
    rating: 4.3,
    reviewCount: 850,
    review: 'Excellent boat noodles 20฿ per bowl. Famous near Suan Sunandha. Cash only.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Toy Kuay Teow Ruea (ต้อยก๋วยเตี๋ยวเรือ)',
    address: '18 Ratchawithi Alley, Ratchathewi, Bangkok 10400',
    district: 'Ratchathewi',
    hours: 'Mo-Su 08:00-17:00',
    rating: 4.4,
    reviewCount: 380,
    review: 'Best boat noodles in Bangkok (Toys). Alley off Soi Ratchawithi 18. Take Grab bike.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Doy Kuay Teow Reua (ดอยก๋วยเตี๋ยวเรือ)',
    address: '15 Ratchawithi Rd, Thung Phaya Thai, Ratchathewi, Bangkok 10400',
    district: 'Phaya Thai',
    hours: 'Mo-Su 08:00-18:00',
    rating: 4.5,
    reviewCount: 420,
    review: 'Best traditional boat noodles. Near Victory Monument BTS. Boat noodles 15฿.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Sud Yod Kuay Teow Reua (Pa Yuk)',
    address: 'Victory Monument boat noodle alley, Ratchathewi, Bangkok',
    district: 'Phaya Thai',
    phone: '02-271-3178',
    hours: 'Mo-Su 11:00-21:00',
    rating: 4.0,
    reviewCount: 250,
    review: 'Famous Victory Monument boat noodle alley. Cheap, tasty. Take BTS to Victory Monument, walk to Fashion Mall, descend skywalk.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Teow Rua Silom (เตี๋ยวเรือสีลม)',
    address: 'Silom area, Bang Rak, Bangkok 10500',
    district: 'Silom',
    rating: 4.3,
    reviewCount: 180,
    review: 'Great boat noodle spot in Silom. Authentic Thai boat noodles.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Guay Tiew Ruea Khon Rum (ก๋วยเตี๋ยวเรือคนรุม)',
    address: 'Khlong Toei Market, Khlong Toei, Bangkok 10110',
    district: 'Khlong Toei',
    rating: 4.3,
    reviewCount: 20,
    review: 'Authentic Thai boat noodles. Parking convenience, affordable prices, flavorful Pad Thai.',
    category: 'ก๋วยเตี๋ยว',
  },

  // Rice / Khao Man Gai (more)
  {
    name: 'Watsana Khao Man Gai (วัฒนา ข้าวมันไก่)',
    address: '9/275 Phuttha Bucha Rd, Bang Mot, Chom Thong, Bangkok 10150',
    district: 'Chom Thong',
    hours: 'Mo-Su 06:00-19:00',
    rating: 4.3,
    reviewCount: 600,
    review: 'Michelin Bib Gourmand 2024. Humble shophouse famous for chicken rice + newer noodle offerings. Long queues.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Nai Ho Chicken Rice (ไหหน่อ ไก่ข้าวมัน)',
    address: 'Bangkok (Bib Gourmand 2024 newcomer)',
    district: 'Bang Kapi',
    rating: 4.4,
    reviewCount: 350,
    review: 'Long-queue street food. Aromatic chicken rice with succulent meat. Singaporean chef Ah Ho family recipes.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Kuang Heng Pratunam (กวงเฮงประตูน้ำ)',
    address: 'Soi Petchaburi 30, New Petchaburi Rd, Makkasan, Ratchathewi, Bangkok',
    district: 'Ratchathewi',
    hours: 'Mo-Su 05:30-15:30, 17:00-03:00',
    rating: 4.0,
    reviewCount: 800,
    review: 'The green-jersey Pratunam chicken rice. Affordable classic. 30฿ plate.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Toh Kim (โต๊ะคิม) Thai Chicken Rice (Emquartier)',
    address: 'EmQuartier, Sukhumvit Rd, Phrom Phong, Bangkok 10110',
    district: 'Phrom Phong',
    hours: 'Mo-Su 10:00-22:00',
    rating: 4.2,
    reviewCount: 200,
    review: 'Hainanese-style chicken rice at EmQuartier. Tender chicken, fragrant rice.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Ruenton Restaurant (Montien Hotel)',
    address: 'Montien Hotel, 54 Surawong Rd, Bang Rak, Bangkok 10500',
    district: 'Bang Rak',
    rating: 4.3,
    reviewCount: 400,
    review: 'Luxury chicken rice at Montien Hotel. Famous for authentic Hainanese chicken rice since 1964.',
    category: 'ข้าวราดแกง',
  },

  // Cafe (more)
  {
    name: 'One Ounce for Onion (Ekkamai)',
    address: 'Ekkamai area, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Ekkamai',
    rating: 4.4,
    reviewCount: 280,
    review: 'Hidden gem in Ekkamai. Relaxed vibe, top-notch specialty coffee. Hand-roasted beans.',
    category: 'คาเฟ่',
  },
  {
    name: 'Phils (Thonglor side street)',
    address: 'Thonglor Soi 4, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Thonglor',
    hours: 'Mo-Su 08:00-19:00',
    rating: 4.4,
    reviewCount: 350,
    review: 'Older specialty coffee shop. Cozy vibe. Top-notch beans from Thailand + abroad. Hand-roasted in-house.',
    category: 'คาเฟ่',
  },
  {
    name: 'Glow (Sukhumvit Thonglor)',
    address: 'Sukhumvit Rd (heart of Thonglor), Khlong Toei Nuea, Bangkok 10110',
    district: 'Thonglor',
    hours: 'Mo-Su 07:00-19:00',
    rating: 4.3,
    reviewCount: 220,
    review: 'Friendly staff, great pour-over selection. Small but cozy specialty spot.',
    category: 'คาเฟ่',
  },
  {
    name: 'Hands and Heart (Ekkamai 2)',
    address: 'Ekkamai Soi 2, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Ekkamai',
    hours: 'Mo-Su 07:30-17:00',
    rating: 4.4,
    reviewCount: 150,
    review: 'Nitro cold brew, flat whites, brunch. Speciality coffee Ekkamai.',
    category: 'คาเฟ่',
  },
  {
    name: 'Lucca (Thonglor)',
    address: 'Back alley off Sukhumvit 63 (Ekkamai), Khlong Tan Nuea, Bangkok 10110',
    district: 'Ekkamai',
    hours: 'Mo-Su 08:00-17:00',
    rating: 4.3,
    reviewCount: 180,
    review: 'Single-origin filter coffee. Minimalist vibes. Drip Roast recommends as crawl stop.',
    category: 'คาเฟ่',
  },
  {
    name: 'Beans (Thonglor)',
    address: 'Thonglor area, Khlong Toei Nuea, Watthana, Bangkok 10110',
    district: 'Thonglor',
    hours: 'Mo-Su 08:00-18:00',
    rating: 4.5,
    reviewCount: 320,
    review: 'The best coffee in Bangkok per Drip Roast. Walk in, order whatever pouring, walk out a believer.',
    category: 'คาเฟ่',
  },

  // Bakery (more)
  {
    name: 'Jean Philippe at The Commons (Thonglor 17)',
    address: '3/F The Commons, 335 Thonglor 17, Khlong Tan Nuea, Bangkok 10110',
    district: 'Thonglor',
    hours: 'Mo-Su 08:00-21:00',
    rating: 4.4,
    reviewCount: 400,
    review: 'Jean Philippe fresh-baked baguettes 80฿, sourdough rolls, pure butter croissants 60฿, classic French desserts.',
    category: 'เบเกอรี่',
  },
  {
    name: 'Amantee (Ekkamai 22)',
    address: '172 Phran Nok Phutthamonthon Sai 4 Rd, Bang Phrom, Taling Chan, Bangkok',
    district: 'Taling Chan',
    hours: 'Mo-Su 08:00-19:00',
    rating: 4.4,
    reviewCount: 280,
    review: 'French family bakery. Delectable croissants + pastries. Functions as gallery + cafe.',
    category: 'เบเกอรี่',
  },
  {
    name: 'Konnichipan Bakery (Phra Nakhon)',
    address: '183 Chakrabongse Rd, Talat Yot, Phra Nakhon, Bangkok 10200',
    district: 'Phra Nakhon',
    phone: '+66 2 629 3270',
    hours: 'Mo-Su 08:00-19:00',
    rating: 4.3,
    reviewCount: 250,
    review: 'Hidden gem bakery in Old Town. Japanese-French style. 30-year rye sourdough.',
    category: 'เบเกอรี่',
  },
  {
    name: 'Bartels Sathorn',
    address: '167/4-6 Suan Phlu Rd (Sathorn Soi 3), Bang Rak, Bangkok',
    district: 'Bang Rak',
    rating: 4.4,
    reviewCount: 200,
    review: 'Bartels Scandi-inspired sourdough. Open-faced sandwiches, cinnamon rolls, Sathorn location.',
    category: 'เบเกอรี่',
  },
  {
    name: 'Beyond Bread (Sukhumvit 13)',
    address: '28 Soi Sukhumvit 13, Khlong Toei Nuea, Watthana, Bangkok 10110',
    district: 'Sukhumvit',
    phone: '092 276 6514',
    hours: 'Mo-Su 07:00-21:00',
    rating: 4.4,
    reviewCount: 220,
    review: 'French Chef Guillaume Lansoy. Sourdough 160฿, butter croissant 70฿. Croissant+coffee 99฿ 7-9am.',
    category: 'เบเกอรี่',
  },
  {
    name: 'Bartels Sukhumvit 33 (Punnawithi)',
    address: 'Punnawithi branch, True Digital Park, Sukhumvit, Bangkok 10110',
    district: 'Phra Khanong',
    hours: 'Mo-Su 07:00-18:00',
    rating: 4.3,
    reviewCount: 150,
    review: 'Bartels True Digital Park branch. Same great sourdough, sandwiches, pastries.',
    category: 'เบเกอรี่',
  },
  {
    name: '9 Pastry (W District, Phra Khanong)',
    address: 'W District, Sukhumvit, Phra Khanong, Bangkok',
    district: 'Phra Khanong',
    hours: 'Mo-Su 08:00-17:00',
    rating: 4.3,
    reviewCount: 120,
    review: 'Flaky laminated pastries, sandwiches, hearty burekas. Neighbourhood gem in W District.',
    category: 'เบเกอรี่',
  },
  {
    name: 'Tiengna (Sukhumvit Soi 39)',
    address: '3-4 Soi Phrom Si (Sukhumvit Soi 39), Khlong Tan Nuea, Bangkok 10110',
    district: 'Sukhumvit',
    phone: '061-174-9839',
    hours: 'Mo-Su 07:30-17:00',
    rating: 4.4,
    reviewCount: 180,
    review: 'Famous Bangkok croissants. Donuts, creative drinks. Best croissants in Bangkok.',
    category: 'เบเกอรี่',
  },

  // Papaya / Som Tam (more)
  {
    name: 'Phed Phed Somtam (Chidlom)',
    address: 'Central Chidlom branch, Ploenchit Rd, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    hours: 'Mo-Su 10:00-21:00',
    rating: 4.0,
    reviewCount: 300,
    review: 'Chidlom branch is the standout. Amazing papaya salad and Issan food. Spicy authentic flavors.',
    category: 'ส้มตำ',
  },
  {
    name: 'Som Tam Jay So (Chidlom branch)',
    address: 'Soi Sanam Khli, Lumphini, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    hours: 'Mo-Su 11:00-21:00',
    rating: 4.2,
    reviewCount: 200,
    review: 'Som Tam Jay So branch in Pathum Wan. Authentic Isaan som tam, fiery + pungent flavors.',
    category: 'ส้มตำ',
  },

  // Salad (more)
  {
    name: 'Tonic (Sukhumvit 39)',
    address: 'Baan Prompong, Sukhumvit Soi 39, Khlong Tan Nuea, Bangkok 10110',
    district: 'Phrom Phong',
    phone: '084-424-9454',
    hours: 'Mo-Su 09:00-20:00',
    rating: 4.4,
    reviewCount: 280,
    review: 'Wholesome bowls. Healthy + hearty. Phrom Phong location. 9AM-8PM.',
    category: 'สลัด',
  },
  {
    name: 'Ohkajhu Organic Farm (Phra Nakhon)',
    address: '179 Thanon Atsadang, Wang Burapha Phirom, Phra Nakhon, Bangkok 10200',
    district: 'Phra Nakhon',
    hours: 'Thu-Tue 09:00-19:00',
    rating: 4.2,
    reviewCount: 220,
    review: 'Organic Thai cuisine from farm to table. Salad-focused. 65-98฿ food. Old Town location.',
    category: 'สลัด',
  },
  {
    name: 'Farm to Table Organic Cafe (Sukhumvit 24)',
    address: '46/1 Sukhumvit 24 Alley, Khlong Tan, Khlong Toei, Bangkok 10110',
    district: 'Khlong Toei',
    hours: 'Mo-Su 09:00-19:00',
    rating: 4.3,
    reviewCount: 150,
    review: 'Health-conscious organic Thai. Local produce, fresh + seasonal. Sukhumvit location.',
    category: 'สลัด',
  },
  {
    name: 'Vistro (Sukhumvit)',
    address: 'Sukhumvit area, Watthana, Bangkok',
    district: 'Sukhumvit',
    rating: 4.2,
    reviewCount: 80,
    review: 'Phuket-origin salad-only joint. Best quality veggies sourced locally + overseas. House dressings.',
    category: 'สลัด',
  },

  // Bar / Pub (more)
  {
    name: 'The Speakeasy Rooftop Bar (Hotel Muse)',
    address: '24-25/F Hotel Muse Bangkok, 55/888 Langsuan, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    rating: 4.7,
    reviewCount: 600,
    review: 'Two-floor Prohibition-era rooftop. King Rama V travel theme. Outstanding cocktails, great vibe.',
    category: 'ผับบาร์',
  },
  {
    name: 'Abandoned Mansion (Sukhumvit)',
    address: 'Sukhumvit area, Watthana, Bangkok 10110',
    district: 'Sukhumvit',
    hours: 'Mo-Su 19:00-01:00',
    rating: 4.5,
    reviewCount: 250,
    review: 'Best speakeasy in Bangkok per Two Passports Packed. Hidden entrance, creative cocktails.',
    category: 'ผับบาร์',
  },
  {
    name: 'Sanctuary Rooftop',
    address: 'Bangkok (Sanctuary hotel rooftop)',
    district: 'Pathum Wan',
    hours: 'Mo-Su 17:00-00:00',
    rating: 4.4,
    reviewCount: 180,
    review: 'Rooftop bar with view of Bangkok. Cozy atmosphere, good cocktails.',
    category: 'ผับบาร์',
  },
  {
    name: '008 Bar (MUU Bangkok Hotel)',
    address: '11/F MUU Bangkok Hotel, 88/333 Soi Sukhumvit 55, Thonglor, Bangkok 10110',
    district: 'Thonglor',
    hours: 'Mo-Su 19:00-02:00',
    rating: 4.4,
    reviewCount: 200,
    review: 'Prohibition-era cocktails. Live jazz. Elegant secret setting. Signature cocktails 420฿.',
    category: 'ผับบาร์',
  },
  {
    name: 'Iron Balls Distillery (Ekkamai)',
    address: 'Sukhumvit Soi 63, Ekkamai, Khlong Tan Nuea, Bangkok 10110',
    district: 'Ekkamai',
    hours: 'Mo-Su 19:00-02:00',
    rating: 4.4,
    reviewCount: 280,
    review: 'Visually striking gin distillery + bar. Iron Balls Gin Tonic, Tropical Punch. 400-700฿.',
    category: 'ผับบาร์',
  },
  {
    name: 'The Lab Bar (Ekkamai 10)',
    address: 'Ekkamai Soi 10, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Ekkamai',
    hours: 'Mo-Su 18:00-02:00',
    rating: 4.2,
    reviewCount: 150,
    review: 'Futuristic style cocktail bar. Well-known Ekkamai hangout. Late-night spot.',
    category: 'ผับบาร์',
  },
  {
    name: 'ATMOS Thonglor 10',
    address: '308 Sukhumvit Soi 5, Khlong Toei Nuea, Watthana, Bangkok 10110',
    district: 'Sukhumvit',
    hours: 'Mo-Su 17:00-00:00',
    rating: 4.1,
    reviewCount: 100,
    review: 'Thonglor Bistro Bar. Atmos Thonglor 10. Daily 5pm-12am. 10+ signature cocktails handcrafted.',
    category: 'ผับบาร์',
  },
  {
    name: 'Bliss Ekkamai',
    address: '124 Soi Sukhumvit 63, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Ekkamai',
    hours: 'Mo-Su 18:00-02:00',
    rating: 4.0,
    reviewCount: 120,
    review: 'Bistro bar that wants to be a nightclub. Ekkamai vibes. Daily 6pm-2am.',
    category: 'ผับบาร์',
  },
  {
    name: 'Beer Belly BKK',
    address: 'Ekkamai area, Watthana, Bangkok',
    district: 'Ekkamai',
    hours: 'Mo-Su 17:00-02:00',
    rating: 4.2,
    reviewCount: 90,
    review: 'Specialist in draft beer. Beer Pong, table tennis, darts. Vibrant atmosphere.',
    category: 'ผับบาร์',
  },

  // Dessert (more)
  {
    name: 'Make Me Mango (Mango Cafe)',
    address: 'Bangkok (Siam Square area)',
    district: 'Pathum Wan',
    rating: 4.5,
    reviewCount: 350,
    review: 'Best mango bingsu + mango sticky rice. Gorgeous spot for dessert break. Mango-focused cafe.',
    category: 'ของหวาน',
  },
  {
    name: 'After You (Siam Paragon)',
    address: '991/1 Rama 1 Rd, G Floor Siam Paragon, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    hours: 'Mo-Su 10:00-22:00',
    rating: 4.5,
    reviewCount: 5000,
    review: 'Famous kakigori (Thai shaved ice). Mango sticky rice kakigori is to-die-for. Multiple BKK locations.',
    category: 'ของหวาน',
  },
  {
    name: 'After You (Terminal 21)',
    address: '60-64 Soi Sukhumvit 19, Level 1 Terminal 21, Asok, Bangkok 10110',
    district: 'Sukhumvit',
    rating: 4.4,
    reviewCount: 2000,
    review: 'Hojicha Bingsu + Thick Toast. After You Terminal 21 branch. SGD 10/person.',
    category: 'ของหวาน',
  },
  {
    name: 'Kor Panich (ก.พานิช)',
    address: 'Bangkok (Old Town, near Khao San)',
    district: 'Phra Nakhon',
    rating: 4.4,
    reviewCount: 400,
    review: 'Famous traditional Thai dessert shop. Mango sticky rice + traditional sweets. Near Khao San.',
    category: 'ของหวาน',
  },
  {
    name: 'Mae Varee (แม่วารี) (Thonglor)',
    address: '1 Soi Sukhumvit 55, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Thonglor',
    hours: 'Mo-Su 09:00-22:00',
    rating: 4.5,
    reviewCount: 800,
    review: 'Famous mango sticky rice spot. Open late, post-midnight dessert destination. ~150฿.',
    category: 'ของหวาน',
  },
  {
    name: 'The Wicked Snow (Chula 22)',
    address: '2/F I\'m Park, Chula Soi 22, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    hours: 'Mo-Su 11:00-21:00',
    rating: 4.1,
    reviewCount: 200,
    review: 'Korean bingsu. Softest ground frozen milk. Mango, Oreo, matcha flavors. Bingsu 120-240฿.',
    category: 'ของหวาน',
  },

  // Late night (more)
  {
    name: 'A-Ramen (Samyan Mitrtown)',
    address: 'Samyan Mitrtown 1/F, 944/1 Rama IV Rd, Wang Mai, Pathum Wan, Bangkok 10330',
    district: 'Pathum Wan',
    phone: '02-219-1519',
    hours: '24 hours',
    rating: 4.2,
    reviewCount: 350,
    review: '24-hour ramen restaurant. Japanese-style design. Dedicated solo-diner booths. Near MRT Sam Yan.',
    category: 'เปิดดึก',
  },
  {
    name: 'Chok Dee Dim Sum (Ekkamai)',
    address: 'Ekkamai Rd, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Ekkamai',
    hours: '24 hours',
    rating: 3.9,
    reviewCount: 150,
    review: 'Most of Chok Dee 15 branches in Bangkok open 24/7. Dim sum, noodle, rice dishes. Nurse late-night crowd.',
    category: 'เปิดดึก',
  },
  {
    name: 'News Rifa (Sukhumvit 33)',
    address: '12/19 Sukhumvit Soi 33, Khlong Toei Nuea, Watthana, Bangkok 10110',
    district: 'Sukhumvit',
    hours: 'Daily 17:00-04:00',
    rating: 4.0,
    reviewCount: 80,
    review: 'Open daily 5pm-4am. Indian food. Late-night favorite in Sukhumvit area.',
    category: 'เปิดดึก',
  },
  {
    name: 'Took Lae Dee (Foodland Patpong)',
    address: '9 Patpong Rd, Bang Rak, Bangkok 10500',
    district: 'Bang Rak',
    phone: '02-234-4558',
    hours: '24 hours',
    rating: 3.9,
    reviewCount: 200,
    review: '24-hour food bar. Western breakfast under 100฿. Best 4am pad kra pao (111฿) in BKK. Foodland chain.',
    category: 'เปิดดึก',
  },
  {
    name: 'V8 Diner (Bangkok)',
    address: 'Bangkok (V8 diner multiple locations)',
    district: 'Pathum Wan',
    hours: '24 hours',
    rating: 3.8,
    reviewCount: 80,
    review: 'Late-night diner open 24/7. Western comfort food. Late-night dining in BKK.',
    category: 'เปิดดึก',
  },
  {
    name: 'Cafe SWISS Restaurant & Bar (Sukhumvit 11)',
    address: 'Sukhumvit Soi 11, Khlong Toei Nuea, Watthana, Bangkok 10110',
    district: 'Sukhumvit',
    hours: '24 hours',
    rating: 4.0,
    reviewCount: 150,
    review: '24-hour Swiss restaurant + bar. Late-night European comfort food + drinks.',
    category: 'เปิดดึก',
  },
  {
    name: 'Che Kiang (Lat Phrao)',
    address: '97 Chok Chai 4 Rd, Lat Phrao, Bangkok 10230',
    district: 'Lat Phrao',
    phone: '02-933-3118',
    hours: '24 hours',
    rating: 3.8,
    reviewCount: 100,
    review: '24-hour Chinese-Thai eatery. Late-night meals in Lat Phrao. Open when hunger strikes.',
    category: 'เปิดดึก',
  },
  {
    name: 'Sunrise Tacos (Sukhumvit 12-14)',
    address: 'Between Sukhumvit Soi 12 and 14, Khlong Toei, Bangkok 10110',
    district: 'Sukhumvit',
    hours: '24 hours',
    rating: 3.9,
    reviewCount: 200,
    review: '24-hour Mexican food. Late-night burritos + tacos. Takeaway around the clock.',
    category: 'เปิดดึก',
  },
  {
    name: 'Ban Rie Coffee (Ekkamai)',
    address: 'Ekkamai Rd, Khlong Tan Nuea, Watthana, Bangkok 10110',
    district: 'Ekkamai',
    hours: '24 hours',
    rating: 4.0,
    reviewCount: 80,
    review: '24-hour coffee joint on Ekkamai Rd. Late-night coffee for self-employed workers + night owls.',
    category: 'เปิดดึก',
  },
  {
    name: 'Bug & Bee (Silom)',
    address: 'Silom Rd, Suriya Wong, Bang Rak, Bangkok 10500',
    district: 'Bang Rak',
    hours: '24 hours',
    rating: 4.2,
    reviewCount: 250,
    review: '24-hour coffee shop. Sanctuary for night owls. Long-running Silom spot for late-night + breakfast.',
    category: 'เปิดดึก',
  },
  {
    name: 'Hollys Coffee (Sukhumvit 15)',
    address: 'Near Sukhumvit Soi 15, Khlong Toei Nuea, Bangkok 10110',
    district: 'Sukhumvit',
    hours: '24 hours',
    rating: 4.0,
    reviewCount: 120,
    review: 'South Korean specialty-coffee franchise. 24-hour Bangkok branch. Late-night coffee culture.',
    category: 'เปิดดึก',
  },
  {
    name: 'Federal Coffee Shop (Sukhumvit 11)',
    address: 'Sukhumvit Soi 11, Khlong Toei Nuea, Watthana, Bangkok 10110',
    district: 'Sukhumvit',
    hours: '24 hours',
    rating: 3.7,
    reviewCount: 80,
    review: '24-hour coffee shop on Sukhumvit Soi 11. Dam Sukhumvit classic. Late-night caffeine.',
    category: 'เปิดดึก',
  },
  {
    name: 'Che Kiang (Lat Phrao - dineguides)',
    address: '97 Chok Chai 4 Rd, Lat Phrao, Bangkok 10230',
    district: 'Lat Phrao',
    hours: '24 hours',
    rating: 4.0,
    reviewCount: 50,
    review: '24-hour Chinese-Thai open when hunger strikes. Lat Phrao area.',
    category: 'เปิดดึก',
  },

  // ============ ROUND 3 — Nonthaburi scope (July 2026) ============

  // Noodle (Nonthaburi)
  {
    name: 'KAITALUK Noodle (ไก่ตะลุก)',
    address: 'Pak Kret, Nonthaburi 11120',
    district: 'Pakkret',
    hours: 'Mo-Su 17:00-22:00 (Fri closed)',
    rating: 4.2,
    reviewCount: 80,
    review: 'Pak Kret boat noodles specialist. Evening-only. Cash only.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Chuan Kitchen (เชือน คิทเช่น)',
    address: 'Pak Kret, Nonthaburi 11120',
    district: 'Pakkret',
    rating: 4.0,
    reviewCount: 60,
    review: 'Pak Kret local kitchen. Authentic Thai-style noodles.',
    category: 'ก๋วยเตี๋ยว',
  },
  {
    name: 'Siam Bistro (สยาม บิสโทร)',
    address: 'Bang Bua Thong, Nonthaburi 11110',
    district: 'Bang Bua Thong',
    rating: 4.1,
    reviewCount: 80,
    review: 'BKK Food Guide full day tour stop. Thai food + Bistro fare in Bang Bua Thong.',
    category: 'ก๋วยเตี๋ยว',
  },

  // Rice (Nonthaburi) - ข้าวราดแกง / Khao Mok Gai
  {
    name: 'Chai Phochana (ไชยโภชนา) (Michelin Bib Gourmand)',
    address: 'Nonthaburi (Michelin Bib Gourmand street food)',
    district: 'Mueang Nonthaburi',
    rating: 4.3,
    reviewCount: 200,
    review: 'Michelin Bib Gourmand street food in Nonthaburi. Chinese-Thai cuisine. Crab meat curry. Always busy.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Mong Khon Chai (มงคลชัย)',
    address: 'Nonthaburi',
    district: 'Mueang Nonthaburi',
    hours: 'Mo-Su',
    rating: 4.2,
    reviewCount: 90,
    review: 'Famous Nonthaburi ข้าวมันไก่ (chicken rice). Delicious for many years. Local institution.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Khao Mok Kai Siam (ข้าวหมกไก่สยาม)',
    address: 'Nonthaburi',
    district: 'Mueang Nonthaburi',
    rating: 3.9,
    reviewCount: 60,
    review: 'Thai-style chicken rice. Nonthaburi location.',
    category: 'ข้าวราดแกง',
  },
  {
    name: 'Tam Daek Wey (ตำแดกเวย)',
    address: 'Owl Market, Nonthaburi Bypass Rd, Mueang Nonthaburi 11000',
    district: 'Mueang Nonthaburi',
    phone: '+66 80 434 9999',
    hours: 'Mo-Su 15:00-24:00',
    rating: 4.2,
    reviewCount: 100,
    review: 'Spicy papaya salad at Owl Market. As delicious as other top som tam spots.',
    category: 'ส้มตำ',
  },

  // Cafe (Nonthaburi)
  {
    name: 'Caffeine Thepsadej (คาเฟอีน เทพสถิตย์)',
    address: 'Nonthaburi (Thepsadej area)',
    district: 'Mueang Nonthaburi',
    rating: 5.0,
    reviewCount: 24,
    review: 'Great coffee shop. Top-rated cafe in Nonthaburi. Minimal, focused on coffee.',
    category: 'คาเฟ่',
  },
  {
    name: 'My Brew Specialty Coffee (ปากเกร็ด)',
    address: '183/78 Moo 7, Bypass Road, Bang Talat, Pak Kret 11120',
    district: 'Pakkret',
    phone: '+66 99 192 8982',
    hours: 'Mo-Su 07:00-19:00',
    rating: 4.6,
    reviewCount: 200,
    review: 'Probably the best coffee in Thailand (per traveler). Thai creative cuisine. Free WiFi, parking.',
    category: 'คาเฟ่',
  },
  {
    name: 'Love Cups Cafe (บางบัวทอง)',
    address: 'Bang Bua Thong, Nonthaburi 11110',
    district: 'Bang Bua Thong',
    rating: 5.0,
    reviewCount: 20,
    review: 'Cute cafe in Nonthaburi. Mountain-like atmosphere. Cozy coffee spot.',
    category: 'คาเฟ่',
  },
  {
    name: 'Cafe De Lampho',
    address: 'Bang Bua Thong, Nonthaburi 11110',
    district: 'Bang Bua Thong',
    rating: 4.3,
    reviewCount: 30,
    review: 'Chill cafe. International fare. Local Nonthaburi vibe.',
    category: 'คาเฟ่',
  },
  {
    name: 'Present Simple (เพรสเซนท์ ซิมเปิ้ล)',
    address: 'Bang Bua Thong, Nonthaburi 11110',
    district: 'Bang Bua Thong',
    rating: 4.5,
    reviewCount: 30,
    review: 'Cute cafe. Excellent coffee. Good food. Local Nonthaburi hidden gem.',
    category: 'คาเฟ่',
  },
  {
    name: 'Gateaux House (Bang Bua Thong)',
    address: 'Bang Bua Thong, Nonthaburi 11110',
    district: 'Bang Bua Thong',
    rating: 4.3,
    reviewCount: 20,
    review: 'Bakery + cafe. Cake is good. Local Nonthaburi bakery cafe.',
    category: 'คาเฟ่',
  },
  {
    name: 'Tanwa: The Food Project HQ Cafe',
    address: '100/2 Moo 3, Bang Kruai-Sai Noi Rd, Bang Bua Thong, Nonthaburi 11110',
    district: 'Bang Bua Thong',
    hours: 'Mo-Su 11:00-20:00',
    rating: 4.5,
    reviewCount: 250,
    review: 'Concrete HQ cafe with restaurant, gallery, clothing store. Asian international fare. 100-200฿.',
    category: 'คาเฟ่',
  },
  {
    name: 'Mezzo Coffee (Bang Bua Thong)',
    address: '30 Kanjachapisek Road, Bang Bua Thong, Nonthaburi 11110',
    district: 'Bang Bua Thong',
    rating: 4.2,
    reviewCount: 40,
    review: 'Coffee shop. Nonthaburi Bang Bua Thong location.',
    category: 'คาเฟ่',
  },
  {
    name: 'Plantnery Green Café (Bang Bua Thong)',
    address: 'Bang Yai City Soi 14, Bang Rak Phatthana, Bang Bua Thong, Nonthaburi 11110',
    district: 'Bang Bua Thong',
    hours: 'Tu-Su 10:00-18:00',
    rating: 4.4,
    reviewCount: 60,
    review: 'Plant-based café. Green atmosphere. Vegan-friendly menu.',
    category: 'คาเฟ่',
  },
  {
    name: 'Khun Churn (คุณเชิญ) Vegetarian Buffet',
    address: '88/31 Moo 2, Ngamwongwan Rd, Nonthaburi 11000',
    district: 'Mueang Nonthaburi',
    hours: 'Mo-Su 10:30-17:00',
    rating: 4.3,
    reviewCount: 80,
    review: 'Daily lunch buffet 11:00-14:30 with large variety of salads + Thai dishes. Veg-friendly. Many vegan options.',
    category: 'คาเฟ่',
  },

  // Fast Food (Nonthaburi)
  {
    name: 'KFC (Mueang Nonthaburi)',
    address: 'Bang Kraso, Mueang Nonthaburi, Nonthaburi 11000',
    district: 'Mueang Nonthaburi',
    phone: '+66 65 965 7521',
    hours: 'Mo-Su 10:00-24:00',
    rating: 3.5,
    reviewCount: 150,
    review: 'KFC Nonthaburi main branch. Fried chicken, original recipe. Standard chain experience.',
    category: 'ฟาสต์ฟู้ด',
  },
  {
    name: 'Texas Chicken (Nonthaburi)',
    address: 'Nonthaburi',
    district: 'Mueang Nonthaburi',
    rating: 4.0,
    reviewCount: 11,
    review: 'Texas Chicken branch. Fried chicken fast food. Must try in Nonthaburi.',
    category: 'ฟาสต์ฟู้ด',
  },
  {
    name: 'Bungair Fried Chicken',
    address: 'Nonthaburi',
    district: 'Mueang Nonthaburi',
    rating: 5.0,
    reviewCount: 3,
    review: 'Local Nonthaburi fried chicken. Hidden gem. Crispy + tasty.',
    category: 'ฟาสต์ฟู้ด',
  },
  {
    name: 'Tam Satan Suang (ตำสะท้านซ่วง)',
    address: 'Nonthaburi',
    district: 'Mueang Nonthaburi',
    rating: 5.0,
    reviewCount: 4,
    review: 'Local fast food. Delicious. Nonthaburi favorite.',
    category: 'ฟาสต์ฟู้ด',
  },
  {
    name: 'Est. 1920 Burger & Beer',
    address: 'Nonthaburi',
    district: 'Mueang Nonthaburi',
    rating: 5.0,
    reviewCount: 1,
    review: 'Eastern European + Armenian burger joint. Craft beer. Nonthaburi classic.',
    category: 'ฟาสต์ฟู้ด',
  },

  // Bakery (Nonthaburi)
  {
    name: 'Buathong Bakery (บัวทองเบเกอรี่)',
    address: '32 Kanchanaphisek Road, Bang Bua Thong, Nonthaburi 11110',
    district: 'Bang Bua Thong',
    rating: 4.2,
    reviewCount: 500,
    review: 'Famous bakery in Thailand. Traditional Thai breads + desserts. Spacious mini-supermarket style.',
    category: 'เบเกอรี่',
  },
  {
    name: 'Bakery For You (B4U) (Bang Bua Thong)',
    address: '9/8 Bang Kruai-Sai Noi Road, Sano Loi, Bang Bua Thong 11110',
    district: 'Bang Bua Thong',
    phone: '+66 84 874 9591',
    hours: 'Mon-Fri 06:30-18:00 (Sat-Sun closed)',
    rating: 4.0,
    reviewCount: 30,
    review: 'Local bakery + cafe. Bang Bua Thong favorite. Fresh baked goods.',
    category: 'เบเกอรี่',
  },
  {
    name: 'Non Bakery (นน เบเกอรี่)',
    address: 'Nonthaburi',
    district: 'Mueang Nonthaburi',
    rating: 4.7,
    reviewCount: 9,
    review: 'Top-rated Nonthaburi bakery. 4.7★. Quality bread + pastries.',
    category: 'เบเกอรี่',
  },

  // Salad (Nonthaburi)
  {
    name: 'Jones Salad (Bang Yai)',
    address: 'Bang Yai, Nonthaburi',
    district: 'Bang Yai',
    hours: 'Mo-Su 10:00-22:00',
    rating: 4.5,
    reviewCount: 50,
    review: 'Great big salads! Healthy food at affordable prices. Bang Yai branch.',
    category: 'สลัด',
  },
  {
    name: 'Jones Salad Western Ratchapruek (Pak Kret)',
    address: 'Western Ratchapruek, Pak Kret, Nonthaburi 11120',
    district: 'Pakkret',
    hours: 'Mo-Su 10:00-22:00',
    rating: 4.3,
    reviewCount: 20,
    review: 'Western Ratchapruek branch of Jones Salad chain. Same great big salads concept.',
    category: 'สลัด',
  },
  {
    name: "Jones' Salad ChaengWattana (Pak Kret)",
    address: '99/9 Room 601 Chaeng Wattana-Pakkred 19 Alley, Pak Kret 11120',
    district: 'Pakkret',
    phone: '+66 94 478 9588',
    hours: 'Mo-Su 10:00-22:00',
    rating: 4.2,
    reviewCount: 10,
    review: 'ChaengWattana branch of Jones Salad. Healthy lunch + dinner spot in Pak Kret.',
    category: 'สลัด',
  },
  {
    name: 'Salad Factory (Behive Mall)',
    address: '50/1211 Popular 3 Road, Behive Mall, Nonthaburi 11120',
    district: 'Mueang Nonthaburi',
    phone: '+66 2 001 5659',
    rating: 3.6,
    reviewCount: 80,
    review: 'European + Thai menu with healthy salads. Bright clean place. Generous portions.',
    category: 'สลัด',
  },
  {
    name: 'MAKAI Acai & Superfood Bar',
    address: 'Nonthaburi',
    district: 'Mueang Nonthaburi',
    rating: 4.0,
    reviewCount: 10,
    review: 'Acai + superfood bowls. Nonthaburi healthy cafe.',
    category: 'สลัด',
  },

  // Bar / Pub (Nonthaburi)
  {
    name: 'Chit Beer (Ko Kret Island)',
    address: '66/1 Moo 1, Ko Kret Subdistrict, Pak Kret 11120',
    district: 'Pakkret',
    rating: 4.4,
    reviewCount: 80,
    review: 'Set right by the river on Ko Kret Island. Wide selection of craft beers including rose beer + Kolsch.',
    category: 'ผับบาร์',
  },
  {
    name: 'TAWANDANG German Brewery (ตะวันแดง)',
    address: '188 Moo 4, Chaeng Wattana Road, Pak Kret 11120',
    district: 'Pakkret',
    phone: '02-960-5511-2',
    hours: 'Mo-Su 17:00-23:59',
    rating: 4.0,
    reviewCount: 500,
    review: 'German brewery + restaurant. Craft German beer. Pak Kret branch. Bavarian-style food.',
    category: 'ผับบาร์',
  },
  {
    name: 'BEERGASM (Pak Kret)',
    address: 'Pak Kret Bypass 38 Rd, Inside Ipark Avenue, Pak Kret 11120',
    district: 'Pakkret',
    phone: '+66 85 008 0080',
    rating: 4.8,
    reviewCount: 30,
    review: '#1 Bars & Pubs in Pak Kret. Craft beer + barbecue. Lively atmosphere.',
    category: 'ผับบาร์',
  },
  {
    name: 'Cloud 9 Club & Restaurant (Pak Kret)',
    address: 'Pak Kret, Nonthaburi 11120',
    district: 'Pakkret',
    hours: 'Mo-Su',
    rating: 4.0,
    reviewCount: 100,
    review: 'Pak Kret club + restaurant. "Reaching for the clouds!" Late-night dance + drinks.',
    category: 'ผับบาร์',
  },
  {
    name: 'Retro Bar & Café (Pak Kret)',
    address: 'ถนนป๊อปปูล่า 1, ตำบลบ้านใหม่, The Portal, Pak Kret 11120',
    district: 'Pakkret',
    phone: '+66 2 006 2054',
    rating: 4.5,
    reviewCount: 30,
    review: 'Retro-themed bar + cafe. Cozy vibes. Late-night Pak Kret hangout.',
    category: 'ผับบาร์',
  },

  // Dessert (Nonthaburi)
  {
    name: 'Brown Sugar Dessert Cafe & Bistro',
    address: '14/89 Leiyng Meuxng Nonthaburi Rd, Bang Kraso, Mueang Nonthaburi 11000',
    district: 'Mueang Nonthaburi',
    phone: '+66 91 632 6559',
    hours: 'Mo-Su 11:00-22:00',
    rating: 5.0,
    reviewCount: 30,
    review: 'Hidden gem dessert cafe near Sanambinnam. Waffles, cold crepes, bingsu, Hokkaido cheese cake. Parking.',
    category: 'ของหวาน',
  },
  {
    name: 'Ikigai Bingsu (อิคิไง บิงซู)',
    address: 'Getkrai Garden Project, Soi Wat Span High, Nonthaburi',
    district: 'Mueang Nonthaburi',
    phone: '082 329 8242',
    hours: 'Mo-Su 12:00-20:00 (Thu closed)',
    rating: 4.3,
    reviewCount: 60,
    review: 'Bingsu cafe near Nonthaburi Prep School. Milk bingsu, green tea bingsu from 69฿. Add chocobanana toppings.',
    category: 'ของหวาน',
  },

  // Late Night (Nonthaburi)
  {
    name: 'Baan Rabiang Nam (บ้านระเบียงน้ำ)',
    address: 'Nonthaburi (riverside area)',
    district: 'Mueang Nonthaburi',
    rating: 4.5,
    reviewCount: 411,
    review: 'Seafood + Asian riverside. Late-night Nonthaburi classic. 411 reviews. Massive popularity.',
    category: 'เปิดดึก',
  },
  {
    name: 'Suki Teenoi (สุกี้เตียน้อย) (Owl Market)',
    address: 'Owl Market, Nonthaburi Bypass Road, Tha Sai, Talat Khwan, Nonthaburi 11000',
    district: 'Mueang Nonthaburi',
    phone: '+66 80 434 9999',
    hours: '24 hours',
    rating: 4.0,
    reviewCount: 200,
    review: '24-hour sukiyaki buffet 299฿. Open late. Crowded even at night. Roast duck + sukiyaki + shabu.',
    category: 'เปิดดึก',
  },
  {
    name: 'Tueng Krueng Restaurant (เติ่งเกริ่ง)',
    address: '15/1 Nonthaburi 12 Road, Bang Kraso, Nonthaburi 11000',
    district: 'Mueang Nonthaburi',
    phone: '+66 93 575 9533',
    hours: 'Mo-Su 10:30-22:30',
    rating: 4.2,
    reviewCount: 30,
    review: 'Rustic Thai food restaurant. Lunch, dinner, brunch, late night, drinks. Nonthaburi local spot.',
    category: 'เปิดดึก',
  },
  {
    name: 'Baan Somtum Phranangklao',
    address: 'Nonthaburi (Phra Nangklao area)',
    district: 'Pakkret',
    rating: 3.3,
    reviewCount: 3,
    review: 'Local Thai + Isaan restaurant. Papaya salad specialty in Pak Kret area.',
    category: 'ส้มตำ',
  },
  {
    name: 'Hua Seng Hong Restaurant (หัวเซ่งฮง)',
    address: 'Nonthaburi',
    district: 'Mueang Nonthaburi',
    rating: 4.5,
    reviewCount: 29,
    review: 'Famous Chinese food in Nonthaburi. Dim sum + Chinese-Thai dishes. Delicious.',
    category: 'เปิดดึก',
  },
  {
    name: 'Owl Market (ตลาดนนท์กลางคืน)',
    address: '14/87 Liang Muang Road, Mueang Nonthaburi 11000',
    district: 'Mueang Nonthaburi',
    hours: 'Mo-Su 16:00-24:00',
    rating: 4.0,
    reviewCount: 100,
    review: 'Night market open evening to midnight. Food stalls, clothes, toys. Near MRT Nonthaburi 1. Late-night Nonthaburi hub.',
    category: 'เปิดดึก',
  },
  {
    name: 'R-HAAN (R-Haan Thai Fine Dining)',
    address: 'Nonthaburi (Michelin)',
    district: 'Mueang Nonthaburi',
    rating: 4.6,
    reviewCount: 100,
    review: 'Michelin Thai fine dining. Authentic Thai cuisine. Reservation recommended.',
    category: 'เปิดดึก',
  },
];

console.log(`Total: ${PLACES.length} places`);

// ===== Convert to restaurant rows =====
const photoFallback = (cat) => {
  // GMap CDN photos not free without API key
  // Use category image as fallback (referenced from app's drawable)
  return null; // null = use app-side fallback
};

const now = Date.now();

function toRow(p, idx) {
  const [lat, lng] = guessCoords(p.address);
  const googlePlaceId = `gmap_synthetic_${idx + 1}`;
  const districtId = resolveDistrictId(p.district);
  if (!districtId) {
    console.warn(`  ⚠️  No district match for "${p.district}" — using empty`);
  }
  // Detect Nonthaburi by district name
  const NONTHABURI_DISTRICTS = ['Pakkret', 'Pak Kret', 'Bang Bua Thong', 'Bang Yai', 'Mueang Nonthaburi', 'Nonthaburi', 'Bang Kruai', 'Sai Noi'];
  const isNonthaburi = NONTHABURI_DISTRICTS.includes(p.district);
  const provinceId = isNonthaburi ? 'nonthaburi' : 'bangkok';
  const provinceName = isNonthaburi ? 'นนทบุรี' : 'กรุงเทพมหานคร';
  return {
    id: `gmap_${(idx + 1).toString().padStart(3, '0')}_${slug(p.name).substring(0, 30)}`,
    name: p.name,
    name_th: p.name,
    category: p.category,
    category_slug: slug(p.category),
    lat,
    lng,
    address: p.address,
    district: p.district,
    province: provinceName,
    tel: p.phone || null,
    website: null,
    rating: p.rating || null,
    review_count: p.reviewCount || null,
    price: null,
    tags: [
      'google_places',
      `district:${p.district}`,
      `province:${provinceId}`,
      `gplaceid:${googlePlaceId}`,
    ],
    source: 'google_places',
    is_favorite: false,
    photo_url: null,
    menu_text: null,
    ai_summary: p.review || null,
    province_id: provinceId,
    district_id: districtId || '',
    opening_hours: p.hours || null,
    capacity: null,
    source_updated_at: now,
  };
}

function slug(s) {
  return (s || '')
    .toString()
    .toLowerCase()
    .replace(/[^a-z0-9ก-๙]/g, '')
    .substring(0, 50);
}

const rows = PLACES.map((p, i) => toRow(p, i));

// ===== Dedupe by name+lat+ lng =====
const seen = new Set();
const unique = rows.filter(r => {
  const key = `${r.name}|${r.lat.toFixed(3)}|${r.lng.toFixed(3)}`;
  if (seen.has(key)) return false;
  seen.add(key);
  return true;
});

console.log(`Unique after dedupe: ${unique.length}`);

// ===== Write preview =====
writeFileSync(
  'demo/google-places-payload.json',
  JSON.stringify(unique.slice(0, 3), null, 2)
);

if (DRY_RUN) {
  console.log('\n--- DRY RUN: first 3 rows ---');
  console.log(JSON.stringify(unique.slice(0, 3), null, 2));
  console.log('\nPass --no-dry-run to actually write to DB');
  process.exit(0);
}

// ===== Force = clear existing google_places first =====
if (FORCE) {
  console.log('\n--force: deleting existing google_places rows...');
  await deleteRows({ source: 'eq.google_places' });
  console.log('Cleared.');
}

// ===== Insert via on_conflict=id (upsert) =====
console.log(`\nUpserting ${unique.length} rows to Supabase...`);
const BATCH = 50;
for (let i = 0; i < unique.length; i += BATCH) {
  const batch = unique.slice(i, i + BATCH);
  try {
    await upsertRows(batch);
    console.log(`  Batch ${i / BATCH + 1}: ${batch.length} rows ✅`);
  } catch (e) {
    console.error(`  Batch ${i / BATCH + 1} FAILED:`, e.message);
    process.exit(1);
  }
}

console.log(`\n✅ Done. ${unique.length} google_places rows in Supabase.`);
console.log('Verify:');
console.log('  psql $SUPABASE_URL -c "SELECT source, COUNT(*) FROM restaurants GROUP BY source;"');
console.log('  Or: node -e "const u=process.env.SUPABASE_URL+..." (same query via REST)');
