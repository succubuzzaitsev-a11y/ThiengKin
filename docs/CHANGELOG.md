# เที่ยงกิน — Changelog

> Version history — ทุกการเปลี่ยนแปลงที่สำคัญ

---

## v1 (2026-07-10 22:00)

**Theme:** แนวคิดแรก

- ชื่อชั่วคราว: "AI คัดร้านอร่อยใกล้ฉัน"
- 9 sections ใน workflow doc
- Near-me mode เดียว (ไม่มี Travel)
- 4-week build plan
- Single data source concept: Google Places
- ไม่ได้คิดเรื่อง "ต่างจังหวัด" ยังโฟกัสกรุงเทพ

---

## v2 (2026-07-10 23:00)

**Theme:** Best-of-Breed

- เปลี่ยนชื่อเป็น **"เที่ยงกิน"** (ว่าง · ไม่มีแอปชื่อนี้)
- เพิ่ม **Best-of-Breed section** — ยืมจุดเด่นจาก:
  - Wongnai (categories, user votes)
  - Google Maps (AI summary, attributes)
  - Kinraidee (random, quick presets)
  - Hungry Hub (multi-language, price range)
  - Dine AI (aspect scores, dish rec)
- เพิ่ม **Rural features** — "คนท้องถิ่นแนะนำ", "เปิดเช้า", "ร้านริมทาง", "ของฝาก"
- 6-week build plan (Week 4 เพิ่ม Rural Features)
- Phase 1.5, 2, 3 labels

---

## v3 (2026-07-11 00:00)

**Theme:** Travel Mode First

- **Brand ใหม่:** "กินดี ตลอดทาง" · "เพื่อนร่วมทางสายกิน"
- **Travel Mode เป็น primary use case** — ไม่ใช่ Near-me แล้ว
- **North Star:** "เปิดแอปต้องเจอร้าน" — ไม่มี empty state
- **Corridor cache** — query ร้านตามเส้นทาง A→B
- **Auto-detect driving** — speed > 20 km/h → เปลี่ยน UI
- **2 โหมด × 3 Phase** structure ใน TL;DR
- **ไฮไลท์ ตจว.:** เลือก **เชียงใหม่** เป็น pilot
- **6-week plan** refactored:
  - Week 2 เป็น "Travel Mode Core (หัวใจของแอป)"
  - Week 4 เน้น "Offline Corridor"
  - Week 5 "Test จริงขับรถกรุงเทพ→เชียงใหม์"
- **Changelog section** ใน workflow (กด `<details>` ดู)

---

## v3.1 (2026-07-11 01:00)

**Theme:** UI Polish (Pro Edition)

- **Mockup v2 (Pro):** Material Design 3 + 8dp grid + design tokens
- **Real Thai data** — "ร้านลุงโภชนา", "ก๋วยเตี๋ยวลูกชาย" ไม่ใช่ Lorem Ipsum
- **RestaurantCard component** — 1 component ใช้ 10+ ครั้ง
- **6 screens:**
  1. Travel Home (auto-detect + quick presets)
  2. Route Result (5-10 จุดแวะ + ETA + detour)
  3. Restaurant Detail (AI summary + เมนูเด่น + นำทาง)
  4. Near-me Filter (light mode)
  5. Loading State (skeleton)
  6. Empty/Offline State ("ใช้ร้าน offline")
- **Design System section** — color tokens, type scale, spacing visualizer
- **Dark vs Light** — Travel = true dark, Near-me = off-white
- **Comparison table** v1 vs v2 (10 dimensions)

---

## v3.2 (2026-07-11 01:30)

**Theme:** AI ตัดออกจาก UI

- **Q1:** Workflow doc แก้ทีหลัง (defer)
- **Q2:** ใช้ "สรุปรีวิว (Ai)" — main text Thai, (Ai) เป็น feature note
- **Changed:** "✨ AI REVIEW SUMMARY" → "✨ สรุปรีวิว (Ai)"
- **Changed:** caption "AI summary" → "สรุปรีวิว (Ai)"
- **Reason:** คนไทย 70% ยังไม่คุ้น "AI" · Apple/Google เองก็เลิกใช้
- **Workflow doc:** ~23 จุดที่มี "AI" — เก็บไว้ทีหลัง (มี (Ai) ในบางจุด)

---

## v3.3 (2026-07-11 01:25)

**Theme:** Brand Colors from Logo (Thieng Tham)

- ใช้โลโก้ "Thieng Tham Development" เป็นที่มาของธีมสี
- **Primary:** 🔴 `#DC2626` (แดง) — เปลี่ยนจาก `#c97b3f` (อำพัน)
- **Accent:** 🟡 `#FACC15` (เหลือง) — เปลี่ยนจาก `#2f6f5e` (เขียว)
- **Open Now:** 🟢 `#16A34A` (เขียว) — เก็บไว้ (semantic)
- **Reason:** แดง-เหลือง → appetite + warmth · ตรงกับ Thieng Tham brand
- **Tagline:** "เปิดแอปก็เจอร้าน" (เป็น one-liner หลัก)
- **Component name:** `ThiengKin` (Kotlin camelCase)
- **Rating star:** เปลี่ยนจาก red เป็น yellow บน light mode (visual fix)
- **Updated files:** MOCKUP.html, MOCKUP.md, README.md

---

## v3.3 (2026-07-11 01:50)

**Theme:** File Structure

- ย้ายไฟล์ทั้งหมดไป `D:\thiengKin\`
- แปลง HTML docs → Markdown (.md)
- **โครงสร้าง:**
  ```
  D:\thiengKin\
  ├── README.md
  ├── docs\
  │   ├── WORKFLOW.md (+ .html)
  │   ├── MOCKUP.md (+ .html)
  │   ├── ARCHITECTURE.md
  │   ├── BEST-OF-BREED.md
  │   ├── CHANGELOG.md ← คุณอยู่ที่นี่
  │   └── RESEARCH.md
  ├── scripts\
  │   ├── setup-chiangmai.mjs
  │   └── package.json
  └── android\
      └── README.md
  ```
- **Added:**
  - `ARCHITECTURE.md` — Tech stack + Data flow + Room schema
  - `BEST-OF-BREED.md` — วิเคราะห์ competitors ละเอียด
  - `RESEARCH.md` — Market research notes
  - `CHANGELOG.md` — ไฟล์นี้

---

## v3.5 (2026-07-11 18:49)
**Theme:** UI Direction Locked — ทางตรง (Direct Path)

- **New mockup:** `docs/MOCKUP-v3.html` (7 screens, single HTML file)
- **Design system v3.0:**
  - **Font:** Sarabun เดียว (display 800, body 400) — ไม่ pair serif+sans
  - **สี:** 4 สี ink `#0F0F0F` · paper `#FAFAFA` · red `#DC2626` · mustard `#FACC15` (semantic green `#16A34A`)
  - **Texture:** 0% — ไม่ใช้ SVG noise
  - **Layout:** Single column scroll — ไม่ใช้ bento
  - **Image:** Gray "FOOD" placeholder — ไม่ใช้ gradient + emoji
- **7 screens:** Travel Home · Route Result · Restaurant Detail · Near-me · Loading · Empty/Offline · Favorites
- **Why:** v2.4 "Anti-AI Pass" ใช้ Fraunces + IBM Plex + paper grain + bento ซึ่งกลายเป็น AI 2024-2025 cliché ไปแล้ว
  v3.0 กลับด้าน → "ทำน้อย แต่แม่น" + ใช้ Thai-native aesthetic (Sarabun 1 ตัว, สีตรงของแบรนด์ Thieng Tham)
- **Route line** เป็น hero element (เพราะแอปนี้คือ "ทาง") — ไม่ใช่ decoration
- **Font sizing:** ทุก heading ใส่ `white-space: nowrap` + `text-overflow: ellipsis` กัน wrap (greeting ลด 24→22px)
- **Screen 7:** Favorites (แทน Province picker / Review submitted)
- **Files:**
  - `docs/MOCKUP-v3.html` ← design source of truth
  - `docs/MOCKUP.md` ← deprecate v2.2 (อ้างอิง v3.0 แทน)
  - Android implementation จะตามมาเมื่อ lock components

---

## 🔮 Upcoming

### v3.4 (planned)
- Android project skeleton (Gradle + Compose + Room)
- filter-data.mjs (Foursquare Place Details)
- Android Kotlin importer (JSON → Room DB)
- Manual curation เริ่มต้น (ร้านดังเชียงใหม์ 30-50 ร้าน)

### v4 (planned)
- Week 2-5: Android development
- Travel Mode UI (dark, big buttons)
- Near-me Mode UI (light, filter)
- AI ranking formula 40/30/15/10/5
- Deep link Google Maps

### v5 (planned)
- Phase 2: Google Places via Supabase Edge Function
- AI Review Summary (Gemini Flash)
- Multi-province (เพิ่มจังหวัดที่ 2)
- English toggle
