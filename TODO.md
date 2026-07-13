# TODO · เที่ยงกิน

> Action items + session handoff — ลบ/complete เมื่อทำเสร็จ

**Last updated:** 2026-07-13 22:32 (Asia/Bangkok)
**Project root:** `D:\thiengKin`
**Git branch:** `main`
**Latest commit:** `d96c3f4` (M5 wrap-up: 4 bug fixes + docs)
**Working tree:** clean (post M5 wrap-up)

---

## ✅ M2 · Supabase setup — DONE (2026-07-12)

- [x] Create Supabase project (account `succubuzzaitsev@gmail.com`, ref `zlntknagzrcoduzxngmx`)
- [x] Schema migrations: `001_initial_schema.sql` + `002_rls_policies.sql` (applied via `scripts/apply-migrations.mjs`)
- [x] Push 7 regions + 77 provinces + 928 districts to Supabase (via `scripts/push-geography.mjs`)
- [x] RLS enabled, public read (anon + authenticated)
- [x] District IDs made unique with `{provinceId}_{districtSlug}` pattern (fixes chaloem_phra_kiat duplicates)
- [x] Sync `data/thailand-geography.json` → `android/app/src/main/assets/thailand-geography.json` (IDs changed)

**⚠️ Pending (M2 follow-up):**
- [ ] Get Supabase **anon / Publishable** key for Android client (`SupabaseClient.kt`)
- [ ] (security) Rotate Supabase service_role key + account password (both leaked in earlier chat)
- [ ] (security) Move `Email.txt` plaintext password to password manager; add to `.gitignore` (DONE 2026-07-12)

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

#### M1.a · Data layer (Province/District tables) ✅ DONE
- [x] `Province.kt` + `District.kt` Room entities (77 + 928)
- [x] `ProvinceDao` + `DistrictDao` (Flow + suspend)
- [x] `GeographyRepository` parse `assets/thailand-geography.json` → seed tables on first launch
- [x] Bundle JSON: `android/app/src/main/assets/thailand-geography.json` (508 KB)
- [x] `Restaurant.provinceId` + `districtId` (additive — `cityId` kept for back-compat)
- [x] `RestaurantDao.observeByProvince()` + `observeByDistrict()` + province-scoped count/latest/delete
- [x] `RestaurantRepository.refreshArea(provinceId, districtId?, bbox, ...)` generic fetch
- [x] `RestaurantRepository.observeByProvince/observeByDistrict` (legacy `observeByCity` kept)
- [x] Importers: `OsmImporter` + `FoursquareImporter` accept `provinceId + districtId?`
- [x] DB v3 → v4 (`fallbackToDestructiveMigration` — wipe OK in Phase 1)
- [x] `ThiengKinApp` wires `GeographyRepository` + triggers `importIfEmpty()` on first launch
- [x] `gradle assembleDebug` → BUILD SUCCESSFUL in 23s
- **Commit:** `14edf81` (2026-07-12)

#### M1.b · UI migration ✅ DONE
- [x] `City.kt` deprecated/removed (still used by CitySelector + TravelHomeViewModel)
- [x] `TravelHomeViewModel`: `selectedCity` → `selectedProvince` + `selectedDistrict`
- [x] Switch `refreshCity()` call site to `refreshArea(provinceId, districtId?, bbox)`
- [x] `LocationRepository.setSelectedCity` → `setSelectedProvince` (or keep both with bridge)
- [x] `JsonImporter` removed (no bundled seed — OSM on-demand only)
- [x] `ProvincePicker.kt` — 2-step ModalBottomSheet (searchable province → district drill-down)
- [x] `BoundingBox.kt` — extracted from City.kt, `toBoundingBox()` extension on Province + District
- [x] Build pass: `gradle :app:compileDebugKotlin --rerun-tasks` → BUILD SUCCESSFUL in 14s
- [ ] **APK smoke test** (pending — ต้อง install + run on emulator/เครื่องจริง)
- **Commit:** `4f0d124` (2026-07-12) — +732 / -664 (net -420 — ลบ curated-city code ออก)

### M2 · Supabase setup (6-8 ชม.)
- [ ] Create Supabase project (re-use account `pornchaisic-cloud`)
- [ ] Schema: `provinces`, `districts`, `restaurants` (read-only public)
- [ ] Push 77 provinces + 928 districts → Supabase `provinces` + `districts` table
- [ ] RLS: public read, no auth needed
- [ ] `SupabaseClient.kt` + Room cache layer

### M3 · OSM nationwide pipeline (6-8 ชม.)
- [x] **M3.a** — `OsmClient.queryBbox(s, w, n, e)` generic bbox query (Node mirror in `scripts/osm-fetch.mjs`)
- [x] **M3.b** — `scripts/osm-parse.mjs` — Overpass JSON → Restaurant[] parser (mirror of `OsmImporter.kt`)
- [x] **M3.c** — `scripts/push-osm.mjs` — push parsed → Supabase `restaurants` table (mirror of `refreshArea()`)
- [x] **M3.d** — Android client reads from Supabase (build verified, 33,442 rows nationwide)
- [ ] TTL 7 days (cache check in script), refresh button in Android UI

#### ✅ M3.d blocker (2026-07-13 01:05) — RESOLVED (commit `8421e94`)
**Bug (fixed):** `scripts/osm-fetch.mjs:133` — `if (provinceId && CITY_BBOX[provinceId] && !useCity)` 
required province to be in CITY_BBOX before trying `GEOGRAPHY_FILE`. For 72 provinces, fell through to default.

**Fix (applied):** changed to `if (provinceId && !useCity)` — always check `GEOGRAPHY_FILE` first.

**Verification (post-sweep):** 33,442 OSM rows across all 77 จังหวัด in Supabase — bbox verified per-province.

### M4 · Province picker UI (3-4 ชม.) ✅ DONE (2026-07-13 18:50)
- [x] Searchable province select (ค้นหา Thai/English) — `ProvincePicker.kt` (M1.b)
- [x] District list within province (chips / list) — `DistrictListContent` (M1.b)
- [x] GPS reverse geocode → auto-detect province on first launch — `TravelHomeViewModel.autoSelectProvinceFromGps()` (M4)
  - ทำ centroid-based nearest match → `setProvince()` (1× per session, ไม่ override manual changes)
- [x] "Change location" UI — pill in `TravelHomeScreen` (M1.b)
- [x] **Search restaurants by name** (M4 bonus) — `SearchInput` refactor + `TravelHomeViewModel.setSearchQuery()`
  - substring match `name` + `nameTh` + `category`, มี clear button (×), keyboard "Search" action
  - Empty state context-aware: "ไม่พบร้านที่ค้นหา" + "ล้างคำค้น" button
- [x] **Fix filter chip mapping → OSM-actual** (M4 bonus) — `FILTERS: Map<String, (Restaurant) -> Boolean>`
  - "ริมทาง" → fast_food + takeaway (เดิม Thai custom tags = 0 matches)
  - "เปิดเช้า" → openingHours != null (proxy: curated/active places)
  - "คนท้องถิ่น" → cuisine:thai + cuisine:regional + cuisine:noodle
  - "ของฝาก" → cafe + coffee_shop + bubble_tea
- [x] **Build verify** — `gradle :app:compileDebugKotlin` → SUCCESSFUL in 21s
- [x] **APK build** — `gradle :app:assembleDebug` → SUCCESSFUL in 15s, APK 18MB

**Files changed (3):**
- `android/app/src/main/java/com/thiengkin/ui/components/SearchInput.kt` — refactor static → static+editable modes
- `android/app/src/main/java/com/thiengkin/ui/screens/travel/TravelHomeScreen.kt` — wire SearchInput value+onValueChange, EmptyState context-aware
- `android/app/src/main/java/com/thiengkin/ui/screens/travel/TravelHomeViewModel.kt` — GPS auto-detect, search query, predicate-based FILTERS

### M5 · Ship MVP (2-3 ชม.) — IN PROGRESS
- [x] `BuildConfig.SUPABASE_ANON_KEY` set (publishable key, RLS-enforced) ✅
- [x] Build APK (17.12 MB, 2026-07-13 21:45) ✅
- [x] Push commits to `succubuzzaitsev-a11y/ThiengKin` (5 commits) ✅
- [x] Bug fixes (4): race v2, chip "ร้านกาแฟ", "เปิดเช้า" parser, label "สรุปรีวิว" ✅
- [ ] **APK smoke test on real device** (BLOCKER — no adb device connected, need user)
- [ ] TODO cleanup
- [ ] (security) Rotate Supabase **secret key** — leaked in chat transcript

**Phase B (after MVP ship):**
- M6: Auth (Supabase Auth, email + Google)
- M7: Reviews + GPS check-in
- M8: Points system + tier
- M9: Anti-cheat (report, cool-down, probation)
- M10: Leaderboard, badges (Explorer = visited 10 provinces)

---

## 📋 Session handoff (2026-07-13 22:30 — M5 wrap-up done, smoke test pending)

### Where we are
- **M0-M4 done:** Province picker + GPS auto-detect + search + OSM-actual filter chips
- **M5 wrap-up done (commits `c9bb809` → `d96c3f4`, all pushed):**
  - 4 bug fixes (race v2, chip "ร้านกาแฟ", "เปิดเช้า" parser, label "สรุปรีวิว")
  - Supabase URL + anon key อยู่ใน `android/gradle.properties` แล้ว (publishable, RLS-enforced)
  - APK 17.12 MB built 2026-07-13 21:45
  - 5 commits pushed to `succubuzzaitsev-a11y/ThiengKin`
- **Supabase live:** `zlntknagzrcoduzxngmx.supabase.co` — 33,442 OSM rows nationwide
- **Next:** APK smoke test on real device → ship MVP

### Pending items for M5
- [ ] **APK smoke test on real device** (BLOCKER — no adb device connected, need user)
  - Verify: GPS prompt, province picker (77 จังหวัด), restaurant list, refresh, search, filter chips
  - Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
  - Logcat: `adb logcat -s "ThiengKinApp:*" "TravelHomeVM:*" "RestaurantRepository:*"`
- [ ] (security) **Rotate Supabase secret key** — leaked in earlier chat (full key in session log, not in repo)
  - https://supabase.com/dashboard/project/zlntknagzrcoduzxngmx/settings/api → "Secret" → regenerate
  - ⚠️ Secret key bypasses RLS — must rotate ASAP
- [ ] (optional) Phase B preparation — auth, reviews, points

### M5 quick start (when device + anon key ready)
```powershell
# 1. Set env
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;C:\Users\Succubuz\AppData\Local\Android\Sdk\platform-tools;$env:Path"

# 2. Put anon key in android/gradle.properties
# SUPABASE_URL=https://zlntknagzrcoduzxngmx.supabase.co
# SUPABASE_ANON_KEY=eyJ...  (Publishable key from dashboard)

# 3. Build + install
cd D:\thiengKin\android
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 4. Smoke test
adb logcat -s "ThiengKinApp:*" "TravelHomeVM:*" "RestaurantRepository:*"
# → open app, allow GPS, verify province auto-detect, tap restaurant, refresh, search, filter
```

### Quick start tomorrow
```powershell
# 1. Set JAVA_HOME
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 2. Build APK
cd D:\thiengKin\android
.\gradlew.bat :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# 3. Install (if emulator running)
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 4. Verify logcat
adb logcat -s "ThiengKinApp:*" "TravelHomeVM:*" "RestaurantRepository:*"
```

### Supabase current state (verified 2026-07-13 18:00)
- restaurants total: **33,442 rows** (all source=osm, 77 จังหวัด)
- source=foursquare: 0 | source=manual: 0
- Bangkok 16,731 + 76 จังหวัด = 33,442 (verified Content-Range)

### Quick start tomorrow
```powershell
# 1. Set JAVA_HOME
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 2. Fix M3.d bug (1 line) — scripts/osm-fetch.mjs:133
#    Old: if (provinceId && CITY_BBOX[provinceId] && !useCity) {
#    New: if (provinceId && !useCity) {
code D:\thiengKin\scripts\osm-fetch.mjs

# 3. Re-run sweep (Phase 1 only, resumable)
cd D:\thiengKin
node scripts/sweep-osm.mjs --only fetch
# expected: ~15-20 min, should create 74+ osm-*.json files (5 pre-existing + 72 new)

# 4. Verify bbox of 3 random new files (must NOT be 18.70/98.85/18.90/99.10)
node -e "const fs=require('fs');for(const f of fs.readdirSync('data').filter(x=>x.match(/^osm-[a-z-]+\.json$/))){try{const d=JSON.parse(fs.readFileSync('data/'+f,'utf8'));const lats=d.elements.map(e=>e.lat).filter(x=>x!=null);const lngs=d.elements.map(e=>e.lon).filter(x=>x!=null);console.log(f,':',Math.min(...lats).toFixed(3),Math.min(...lngs).toFixed(3),Math.max(...lats).toFixed(3),Math.max(...lngs).toFixed(3));}catch(e){}}"

# 5. Push to Supabase (Phase 2 + 3, use --force to refresh)
node scripts/sweep-osm.mjs --skip-fetch

# 6. Verify Android build (M3.d code uncommitted — must compile clean)
cd D:\thiengKin\android
.\gradlew.bat :app:compileDebugKotlin --rerun-tasks

# 7. APK smoke test (ถ้ามี emulator)
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 8. Commit M3.d + sweep results (per RULES.md rule 6)
cd D:\thiengKin
git add -A
git status   # review ก่อน commit
git commit -m "feat(android+osm): M3.d Supabase wire-up + 77-province OSM data"
```

### Working dir context
- **Git config:** `succubuzzaitsev-a11y <succubuzzaitsev@gmail.com>` (ตรงกับ commit history, GitHub account `succubuzzaitsev-a11y/ThiengKin`)
- **Remote:** `git@github.com-succubuzzaitsev:succubuzzaitsev-a11y/ThiengKin.git` (push) + `https://github.com/succubuzzaitsev-a11y/ThiengKin.git` (fetch)
- **SSH key:** `~/.ssh/succubuzzaitsev_push_ed25519` (alias `github.com-succubuzzaitsev`) — verified
- **JAVA_HOME** ไม่ได้ตั้งใน PowerShell session — ต้อง set เองทุกครั้ง (หรือใส่ใน `$PROFILE`)
- **Working tree:** clean (latest commit `d96c3f4`)
- **City.kt ถูกลบแล้ว** — Province.centroid ใช้แทน City.lat/lng ทั้งหมด
- **anon key:** `BuildConfig.SUPABASE_ANON_KEY` ✅ set (publishable, RLS-enforced) — Supabase primary path enabled
- **Secret key:** ⚠️ `sb_secret_...` ที่ใช้ admin scripts ใน `data/push-*` — rotate ASAP (leaked in chat)

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
| 2026-07-13 | (pending) | **chore: M5 wrap-up local — gradle.properties (Supabase keys), TravelHomeVM race v2, label fixes, _check.ps1, ssh key backup** |
| 2026-07-13 | `d96c3f4` | **chore: ignore sweep pipeline artifacts + underscore-prefixed debug scripts** |
| 2026-07-13 | `14e5c5d` | **fix(android): M5 picker UX + geography seed race + refresh cancel** |
| 2026-07-13 | `079938e` | **docs(todo): mark M4 + docs done; M5 in progress with clear blockers** |
| 2026-07-13 | `8ca1c9d` | **docs: update README + CHANGELOG to v4 nationwide state** |
| 2026-07-13 | `c9bb809` | **feat(android): M4 province picker finalize — GPS auto-detect, search by name, OSM-actual filter chips** |
| 2026-07-13 | `8421e94` | **feat(osm): M3.d Android client + nationwide sweep (77จังหวัด, 33,442 OSM rows)** |
| 2026-07-13 | `ac6300d` | **feat(osm): M3.c push pipeline — parsed OSM → Supabase restaurants (mirror refreshArea)** |
| 2026-07-12 | `be810bf` | **feat(supabase): M2 Supabase setup — schema, RLS, geography push (7r/77p/928d)** |
| 2026-07-12 | `4f0d124` | **feat(android): M1.b UI migration — drop City/CitySelector/JsonImporter, wire ProvincePicker** |
| 2026-07-12 | `65280f9` | **feat(osm): M3.b parser — Overpass JSON → Restaurant[] (Node mirror of OsmImporter.kt)** |
| 2026-07-12 | `a1f79fd` | **feat(osm): M3.a Overpass fetcher (province/city/bbox) + OsmClient center** |
| 2026-07-12 | `9a0b211` | docs: mark M1.a schema migration done in TODO.md |
| 2026-07-12 | `14edf81` | feat(android): M1.a schema migration — Province/District tables + Restaurant.provinceId/districtId |
| 2026-07-12 | `3e9a131` | feat(data): M0 Thailand province + district reference data (77p/928d/7r) |
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
