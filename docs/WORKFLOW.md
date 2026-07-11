# เที่ยงกิน — Workflow & Architecture

> Version 3 (Final) · Travel Mode First · Chiang Mai Pilot
> ดู HTML version: [WORKFLOW.html](../WORKFLOW.html) (original render)

---

## 🎯 North Star

> **"ขับรถทั้งวัน เปิดแอปก็เจอร้าน"** — เปิดแอปแล้วต้องเจอร้าน ไม่มี empty state

---

## 01 · TL;DR — 2 โหมด × 3 Phase

### 🚗 Travel Mode (Killer Feature · หลัก)

**ขับรถ/เดินทาง → เปิดแอปก็เจอร้าน**

| Field | Value |
|-------|-------|
| **Input** | "จะไปไหน?" + GPS auto-detect |
| **Output** | 5-10 จุดแวะ + ETA + detour |
| **Data** | Corridor cache (ร้านตามเส้นทาง) |
| **Guarantee** | ไม่มี empty state (ขยายรัศมีอัตโนมัติ) |

### 📍 Near-me Mode (สำรอง)

**อยู่ที่เดิม → หาร้านรอบๆ**

| Field | Value |
|-------|-------|
| **Input** | Filter: ระยะ/คะแนน/รีวิว/เปิดอยู่/ประเภท |
| **Output** | Top 20 เรียงตาม AI score |
| **Data** | Room DB query |
| **UX** | Filter UI เต็มรูปแบบ |

### Phases

- **Phase 1 (Week 1-5):** Local-First MVP · 1 จังหวัด · Foursquare · $0
- **Phase 1.5 (Week 3-4):** +Wongnai / Rural · Categories, Quick presets, "คนท้องถิ่น"
- **Phase 2 (Week 6+):** Google Places + AI · ร้านเล็กร้านซอย + Chat · $0* (1-2 users)

---

## 02 · System Architecture

```
📱 Android UI (Compose)
   ├─ 🚗 Travel Mode (Dark)  — primary
   └─ 📍 Near-me Mode (Light)

🤖 AI Ranking (on-device)
   └─ 40/30/15/10/5 formula · Corridor + Route-aware

🗺️ Google Maps SDK
   └─ Display + "Open in Maps" deep link

🗄️ Room Database (SQLite)
   ├─ restaurants
   ├─ favorites
   ├─ routes
   └─ corridor_cache  ← key for travel mode

🛠️ Setup Script (Node.js)
   └─ Foursquare API → JSON → import to Room

☁️ Supabase Edge Function (Phase 2)
   └─ 🔑 Google Places API key stored here (not in app)

🗃️ Supabase Postgres (Phase 2)
   └─ cached search results · 1-3 hr TTL

🔄 Realtime Sync (Phase 2)
   └─ favorites · history sync between 2 devices

🗺️ External APIs
   ├─ Google Places API (New) — $200 free credit/month
   └─ Foursquare Places API — 100K calls/month free
```

---

## 03 · User Flow

### Travel Mode (หลัก)

```
📱 เปิดแอป
  ↓
🚗 Auto-detect driving (speed > 20 km/h)
  ↓
🛣️ "จะไปไหน?" (input destination)
  ↓
🗄️ Query corridor cache
   (ร้านในรัศมี 20 กม. จากเส้นทาง)
  ↓
🤖 AI rank: distance to route + rating + open now
  ↓
📊 Top 5-10 จุดแวะ
   (พร้อม ETA + detour)
  ↓
[กดร้าน] → 🗺️ Deep link Google Maps
           → ❤️ บันทึก / 📤 แชร์
```

**การันตี:** Travel Mode จะ **ไม่มี empty state** — ถ้าร้านในรัศมี 5 กม. ไม่มี → ขยายเป็น 20 กม. → ถ้ายังไม่มี → แสดงเมืองถัดไปบนเส้นทาง

### Near-me Mode

```
📱 เปิดแอป
  ↓
📋 Filter
   (ระยะ/คะแนน/รีวิว/เปิดอยู่/ประเภท)
  ↓
🔍 กดค้นหา
  ↓
📊 Top 20 results
  ↓
[เลือกร้าน] → Detail / นำทาง
```

---

## 04 · Data Flow

### Phase 1: Local-First

```
🛠️ Setup Script (run ครั้งเดียว)
   ↓
🔍 Foursquare API (100K free/mo)
   ↓
Filter: rating ≥ 4.0, has reviews
   ↓
Save → 🗄️ Room DB

📱 App Search
   ↓
Query local (Room DB)
   ↓
🤖 AI Ranking (on-device)
   ↓
📊 Top 20
```

### Phase 2: Google Places via Edge Function

```
📱 App Search (new area)
   ↓
☁️ Supabase Edge Function (HTTPS)
   ↓
Check cache (1hr fresh?)
   ├─ Yes → Use cache
   └─ No → 🗺️ Google Places API
          ↓
       Save to cache
          ↓
       Return to app
   ↓
🤖 AI rank
   ↓
📊 Top 20
```

---

## 05 · Decision Flow (เลือก data source)

```
🔍 User search
  ↓
มีร้านใน Room DB ในรัศมีนี้?
  ├─ Yes, ครบ → AI rank local → 📊
  └─ น้อย/ไม่มี → เคย query < 1 ชม.?
                    ├─ Yes → Supabase cache → AI rank → 📊
                    └─ No → Edge Function → Google Places
                              ↓
                          Save to cache
                              ↓
                          AI rank → 📊
```

---

## 06 · Best-of-Breed — ยืมจุดเด่นจากแต่ละแอป

### 🥇 Wongnai (Phase 1.5)

**ยืม:**
- Categories ละเอียดแบบ local (ก๋วยเตี๋ยว, ส้มตำ, ข้าวซอย, น้ำตก, ปิ้งย่าง)
- "555 อันดับ" → User Votes กด "อร่อยจริง" เพิ่ม weight
- "แนะนำสำหรับคุณ" → AI เรียนรู้จาก history

**ไม่เอา:** โฆษณา, booking, delivery (คนละ business model)

### 🤖 Google Maps + Gemini (Phase 2)

**ยืม:**
- AI Review Summary — "เด่น: ก๋วยเตี๋ยวเนื้อ / ขาด: ที่จอดรถ"
- Contextual Attributes — filter: WiFi, ที่จอด, Pet-friendly, เปิดดึก
- "Ask Maps" chat — ถาม "อยากกินอะไรดี" ได้ (Gemini Flash)
- Personalization จาก save/favorite

**ไม่เอา:** "Inspiration curated" (เฉพาะเจาะจงเกินไป), Track ทุก search (privacy)

### 🍜 Kinraidee + Jaew Pa Gin (Phase 1)

**ยืม:**
- "วันนี้กินอะไรดี?" random button (Kinraidee)
- Quick presets — 1 tap ได้ร้าน (Jaew Pa Gin style)
- Calorie counter (optional)

**ไม่เอา:** Random menu เฉยๆ (ไม่มี GPS/distance)

### 📅 Hungry Hub + OpenRice (Phase 3)

**ยืม:**
- Multi-language — Thai-first + English toggle
- Price range filter — $ / $$ / $$$ / $$$$
- Deep link ไป Hungry Hub สำหรับ booking

**ไม่เอา:** Booking/Deals เอง (ทำยาก, scale ไม่ได้)

### 🌍 Dine AI + Review Analyzer + Umamii (Phase 3)

**ยืม:**
- Aspect scores — รสชาติ/บริการ/ความคุ้ม/บรรยากาศ
- "เมนูแนะนำ" — Dine AI วิเคราะห์รีวิวแนะนำเมนู
- Social discovery — "เพื่อนคุณชอบร้านนี้"

**ไม่เอา:** Social graph แบบเต็ม (ต้อง community ใหญ่)

### 🏘️ เฉพาะ "เที่ยงกิน" — ต่างจังหวัด (Phase 1-2)

**จุดต่างที่ไม่มีใครเหมือน:**
- "คนท้องถิ่นแนะนำ" — badge พิเศษ
- "เปิดเช้าตี 4-5" — ตลาดเช้า, ก๋วยเตี๋ยวเช้า (เฉพาะ ตจว.)
- "ร้านริมทาง/ริมปั๊ม" — สำหรับคนขับรถทางไกล
- "ของฝากประจำจังหวัด" — น้ำพริก, แหนม, หมูยอ
- "เส้นทางทัวร์อาหาร" — เชียงใหม่ 1 วัน / ภูเก็ต 1 วัน
- Travel mode — "กำลังไปเชียงใหม่" preload ทั้งจังหวัด offline

---

## 07 · Setup & Refresh

### ครั้งแรก (one-time)

1. สมัคร Foursquare API (ฟรี · ไม่ต้องใส่บัตร)
2. ตั้ง `FOURSQUARE_API_KEY` environment variable
3. รัน `node scripts/setup-chiangmai.mjs`
4. Filter + import เข้า Room DB
5. Verify ในแอป · เปิดแอป · เห็นร้านครบ · กดเปิด Maps ได้

### Refresh (รายสัปดาห์/เดือน)

1. รัน `node scripts/setup-chiangmai.mjs --refresh`
2. Diff กับข้อมูลเดิม
3. Push to Room DB (Auto-merge ไม่ทับ favorites)

---

## 08 · Cost Reality

| Item | Cost | Note |
|------|------|------|
| Foursquare (Phase 1) | **$0** | 100K calls/เดือน |
| Google Places (Phase 2) | **$0\*** | $200 free credit/เดือน (1-2 users ใช้ <1%) |
| Supabase | **$0** | Free tier (500MB DB, 2GB bandwidth) |
| Google Maps SDK | **$0** | Free 28K loads/เดือน |
| AI ranking | **$0** | On-device formula |
| **รวม** | **$0** | ตลอดกาล |

---

## 09 · 6-Week Build Plan (Travel Mode First)

### Week 1 — Data Layer (เชียงใหม่ pilot)
- Setup script (Foursquare)
- Room DB schema รวม `corridor_cache` + `routes`
- เก็บ 100-300 ร้านเชียงใหม์ + **ร้านดังริมทาง** (ทางหลวง + ทางด่วน)
- Manual seed: ของฝาก + ร้านเช้า + ปั๊มฯ

### Week 2 — Travel Mode Core (หัวใจของแอป)
- GPS permission + auto-detect driving
- Province picker (เชียงใหม์)
- **Travel Mode UI** (big buttons, dark)
- "จะไปไหน?" input
- Corridor query
- **Top 5-10 จุดแวะ + ETA + detour**
- Deep link Google Maps

### Week 3 — AI + Wongnai-style
- AI ranking formula 40/30/15/10/5 (ปรับ weight ตาม mode)
- Categories แบบ local (ก๋วยเตี๋ยว/ส้มตำ/ข้าวซอย/น้ำตก/ปิ้งย่าง)
- "วันนี้กินอะไรดี?" random
- Quick presets 5-6 อัน

### Week 4 — Rural Features + Offline Corridor
- "คนท้องถิ่นแนะนำ" badge
- Contextual Attributes (WiFi/ที่จอด/Pet/เปิดดึก/รับบัตร)
- "เปิดเช้า" "ร้านริมทาง" "ของฝาก" presets
- **Pre-load corridor ทั้งเส้นทาง offline**

### Week 5 — Polish + Driving Test + Ship
- Favorites + Share
- User votes ("อร่อยจริง")
- AI Personalization
- **Test จริงขับรถกรุงเทพ→เชียงใหม์**
- APK smoke test
- Ship

### Week 6 — Add Province 2 + Phase 2 เริ่ม
- เพิ่มจังหวัดที่ 2
- เริ่ม Phase 2: Google Places via Edge Function

---

## 10 · Quick Reference

### Setup Script (Phase 1)

```bash
# ครั้งแรก
node scripts/setup-chiangmai.mjs \
  --lat 18.7883 \
  --lng 98.9853 \
  --radius 25000

# Refresh
node scripts/setup-chiangmai.mjs --refresh
```

### Edge Function (Phase 2)

```bash
# ตั้ง secret
supabase secrets set GOOGLE_PLACES_API_KEY=AIza...

# Deploy
supabase functions deploy places-search

# Test
curl -X POST $SUPABASE_URL/functions/v1/places-search \
  -d '{"lat":18.78,"lng":98.98,"radius":5000}'
```

---

**ดู UI mockup:** [MOCKUP.md](MOCKUP.md)
**ดู research:** [RESEARCH.md](RESEARCH.md)
**ดู architecture details:** [ARCHITECTURE.md](ARCHITECTURE.md)
