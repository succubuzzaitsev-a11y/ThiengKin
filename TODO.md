# TODO · เที่ยงกิน

> Action items + session handoff — ลบ/complete เมื่อทำเสร็จ

**Last updated:** 2026-07-12 18:15 (Asia/Bangkok)
**Project root:** `D:\thiengKin`
**Git branch:** `main`
**Latest commit:** `3e9a131` (M0: Thailand geography data)

---

## 🆕 Pivot · Nationwide scope (2026-07-12)

**ทำไม:** app ต้องใช้ได้ทั่วประเทศ ไม่ใช่แค่ 10 เมืองท่องเที่ยว
**Strategy:**
- ❌ ไม่ seed curated (manual + FSQ เฉพาะเชียงใหม่)
- ✅ ใช้ OSM Overpass API เป็น data source หลัก (ฟรี, ครอบคลุม)
- ✅ Province picker (searchable, 77 จังหวัด) + District drill-down
- ✅ GPS auto-detect เป็น default first launch
- ❌ ไม่ทำ auth/reviews ก่อน — เพิ่ม Phase B ทีหลัง

**Milestone breakdown:**

### M0 · Province/district data ✅ DONE
- [x] Scrape 77 provinces + 928 districts + 7 regions จาก `chingchai/OpenGISData-Thailand`
- [x] Parse GeoJSON → `data/thailand-geography.json` (311 KB)
- [x] ทุก entry มี: id, nameTh, nameEn, bbox, centroid, areaSqkm
- **Commit:** `3e9a131` (2026-07-12)

### M1 · Schema migration (4-6 ชม.)
- [ ] `City.kt` → `Province.kt` + `District.kt` (ใหม่ทั้งหมด)
- [ ] `Restaurant.cityId` → `provinceId` + `districtId` (migration script)
- [ ] `RestaurantDao.observeByCity()` → `observeByProvince(provinceId)` + `observeByDistrict(districtId)`
- [ ] `RestaurantRepository.refreshCity(cityId)` → `refreshArea(bbox, provinceId, districtId)`
- [ ] TravelHomeViewModel: selectedCity → selectedProvince + selectedDistrict

### M2 · Supabase setup (6-8 ชม.)
- [ ] Create Supabase project (re-use account `pornchaisic-cloud`)
- [ ] Schema: `provinces`, `districts`, `restaurants` (read-only public)
- [ ] Push 77 provinces + 928 districts → Supabase `provinces` + `districts` table
- [ ] RLS: public read, no auth needed
- [ ] `SupabaseClient.kt` + Room cache layer

### M3 · OSM nationwide pipeline (6-8 ชม.)
- [ ] `OsmClient.queryBbox(s, w, n, e)` — generic bbox query
- [ ] On-demand fetch: เปิดจังหวัดใหม่ → query OSM → cache in Room + Supabase
- [ ] TTL 30 วัน, refresh button

### M4 · Province picker UI (3-4 ชม.)
- [ ] Searchable province select (ค้นหา Thai/English)
- [ ] District list within province (chips / list)
- [ ] GPS reverse geocode → auto-detect province on first launch
- [ ] "Change location" UI

### M5 · Ship MVP (2-3 ชม.)
- [ ] APK smoke test (offline + online)
- [ ] TODO cleanup
- [ ] README update

**Phase B (after MVP ship):**
- M6: Auth (Supabase Auth, email + Google)
- M7: Reviews + GPS check-in
- M8: Points system + tier
- M9: Anti-cheat (report, cool-down, probation)
- M10: Leaderboard, badges (Explorer = visited 10 provinces)

---

## 📋 Session handoff (2026-07-12 — pivot to nationwide)

### Where we are
- **M0 done:** `data/thailand-geography.json` (77 provinces + 928 districts + 7 regions) — แทน hardcoded 10-city list
- **P0 done:** FoursquareClient v3 wire format fixed (commit `4837679`) — แต่ใน design ใหม่ OSM เป็นหลัก FSQ เป็น optional enhancement
- **Build:** `gradle compileDebugKotlin` → คาดว่ายัง pass (M0 แค่เพิ่ม data file, ไม่แตะ Android)
- **Next:** M1 — schema migration (City.kt → Province.kt + District.kt, restaurant.cityId → provinceId/districtId)

### Quick start tomorrow
```powershell
# 1. Set JAVA_HOME
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 2. Verify build ยัง pass หลัง M0
cd D:\thiengKin\android
.\gradlew.bat compileDebugKotlin

# 3. ดู plan ใหม่
code D:\thiengKin\TODO.md

# 4. เริ่ม M1 (schema migration)
code D:\thiengKin\android\app\src\main\java\com\thiengkin\data\City.kt
code D:\thiengKin\android\app\src\main\java\com\thiengkin\data\Restaurant.kt
```

### Working dir context
- **Git config:** `pornchaisic-cloud <succubuzzaitsev@gmail.com>` (ตรงกับ commit history)
- **No git remote configured** (local-only repo) — push ไม่ได้จนกว่าจะ add remote
- **JAVA_HOME** ไม่ได้ตั้งใน PowerShell session — ต้อง set เองทุกครั้ง (หรือใส่ใน `$PROFILE`)
- **Working tree:** clean (M0 committed at `3e9a131`)
- **Old design (10 cities + FSQ seed) is SUPERSEDED** — แต่โค้ดยังอยู่ จะลบตอน M1

---

## 🔴 P0 · FoursquareClient.kt — wire format fix ✅ DONE

**ไฟล์:** `android/app/src/main/java/com/thiengkin/data/remote/FoursquareClient.kt`
**Commit:** `4837679` (2026-07-12)

Client + Repository ส่ง FSQ v3 ถูกต้องแล้ว:
- ✅ Base URL → `places-api.foursquare.com/places/search`
- ✅ `Authorization: Bearer <key>`
- ✅ `X-Places-Api-Version: 2025-06-17`
- ✅ `query=<text>` + `sort=RELEVANCE` + `offset=<n>`
- ✅ Repository iterate 6 food queries × 3 pages × 50 = up to 900 calls (cap at 30 วัน cache)
- ✅ Dedupe by `fsq_id`

**Verify:** `gradle compileDebugKotlin --rerun-tasks` → BUILD SUCCESSFUL in 29s

**Test ต่อไป:** run `refreshCity("cm")` บนอุปกรณ์/AVD ดูว่า `FSQ saved: N records for cm` (N > 0)

---

## 🟡 P1 · Chiang Mai data → multi-city ⚠️ SUPERSEDED (2026-07-12)

**Status:** เปลี่ยน scope เป็น "nationwide" แล้ว — ดู "🆕 Pivot · Nationwide scope" ด้านบน
- ไม่ต้อง bundle seed (เริ่มจาก 0)
- ไม่ต้อง manual/FSQ curated data
- City.kt + JsonImporter + cityId field จะถูก refactor ใน M1

**Legacy data ที่เก็บไว้ (อาจใช้ใน Phase B หรือ review manual):**
- `data/chiangmai-restaurants-final.json` (292 places, gitignored) — 35 manual + 257 FSQ

---

## 🟢 P2 · Cleanup (optional, low priority)

- [ ] **Untracked file:** `android/app/src/main/assets/chiangmai-restaurants-final.json` (248 KB) — working copy ที่ copy มาตอน validation ไม่ใช่ seed จริง → ลบ หรือเพิ่ม `.gitignore` rule
- [ ] **Warning 1:** `LocationRepository.kt:263` — `getFromLocation` deprecated → migrate ไป `Geocoder` API ใหม่ หรือ wrap
- [ ] **Warning 2:** `TravelHomeViewModel.kt:64` — ขาด `@OptIn(ExperimentalCoroutinesApi::class)` → เพิ่ม annotation

---

## ✅ Done recently (this session + recent)

| Date | Commit | What |
|------|--------|------|
| 2026-07-12 | `3e9a131` | **feat(data): M0 Thailand province + district reference data (77p/928d/7r)** |
| 2026-07-12 | `4aae900` | docs: mark P0 (FoursquareClient v3) as done in TODO.md |
| 2026-07-12 | `4837679` | fix(android): FoursquareClient v3 wire format + Repository query loop (P0) |
| 2026-07-12 | `e93bac3` | docs: add TODO.md (this file) |
| 2026-07-12 | `a006ca2` | fix(android): add missing contentOrNull import in OsmImporter (build was broken) |
| 2026-07-12 | `4bb80b6` | fix(android): FoursquareImporter parse FSQ v3 response format (was returning 0 results) |
| 2026-07-12 | (verify) | `gradle compileDebugKotlin` → SUCCESSFUL in 42s |
| 2026-07-12 | (data) | check-bounds.py → 292/292 in Chiang Mai bounds |
| 2026-07-11 | (data) | Chiang Mai data pipeline: 292 places merged |
| 2026-07-11 | `f219d53` | feat(android): Phase 1.5 multi-city + OSM/FSQ remote data layer (has the bugs we just fixed) |

---

## 🔑 Key files (for tomorrow's context)

| File | Purpose |
|------|---------|
| `RULES.md` | Standing project rules (ห้ามแก้โค้ดก่อนสั่ง, commit ทุก checkpoint, ฯลฯ) |
| `README.md` | Project intro |
| `docs/WORKFLOW.md` | Architecture + workflow (v3) |
| `data/chiangmai-restaurants-final.json` | Chiang Mai data (292 places, gitignored) |
| `data/chiangmai-restaurants.json` | FSQ raw (257 places, gitignored) |
| `scripts/setup-chiangmai.mjs` | Working FSQ v3 fetch pattern (copy shape for Client fix) |
| `scripts/merge-data.mjs` | Merge FSQ + manual → final.json |
| `android/app/src/main/java/com/thiengkin/data/remote/FoursquareImporter.kt` | ✅ Fixed (parses v3) — but in new design OSM is primary, FSQ optional |
| `android/app/src/main/java/com/thiengkin/data/remote/OsmImporter.kt` | ✅ Fixed (import) — will become primary data source in M3 |
| `android/app/src/main/java/com/thiengkin/data/remote/FoursquareClient.kt` | ✅ Fixed in P0 (v3 wire format) — kept as optional enrichment |
| `data/thailand-geography.json` | 🆕 M0 — 77 provinces + 928 districts + 7 regions (committed) |
| `C:\Users\Succubuz\.mavis\scratchpads\mvs_66c806b4008448fea77b747bff463b56\scratchpad.md` | This session's scratchpad (cross-session notes) |
