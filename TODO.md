# TODO · เที่ยงกิน

> Action items + session handoff — ลบ/complete เมื่อทำเสร็จ

**Last updated:** 2026-07-12 01:40 (Asia/Bangkok)
**Project root:** `D:\thiengKin`
**Git branch:** `main`
**Latest commit:** `e93bac3` (TODO.md)

---

## 📋 Session handoff (2026-07-12)

### Where we are
- **Data layer:** Chiang Mai data pipeline เสร็จ — 292 places (35 manual + 257 FSQ), 100% in bounds, validated
- **Build:** `gradle compileDebugKotlin` → **SUCCESSFUL in 42s** (verified end of this session)
- **Code health:** Importer (FSQ v3) แก้แล้ว, OsmImporter แก้แล้ว — **แต่ FoursquareClient ยังเสีย** → ส่ง request ที่ FSQ v3 server ไม่รู้จัก
- **Net effect ตอนนี้:** seed/import ผ่าน assets ทำงานได้, แต่ `refreshCity()` (FSQ path) จะ return 0 records จนกว่าจะแก้ Client

### Quick start tomorrow
```powershell
# 1. Set JAVA_HOME (Android Studio bundled JDK)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 2. Verify build
cd D:\thiengKin\android
.\gradlew.bat compileDebugKotlin

# 3. (ถ้าจะแก้ P0) เปิด FoursquareClient.kt
code D:\thiengKin\android\app\src\main\java\com\thiengkin\data\remote\FoursquareClient.kt
```

### Working dir context
- **Git config:** `pornchaisic-cloud <succubuzzaitsev@gmail.com>` (ตรงกับ commit history)
- **No git remote configured** (local-only repo) — push ไม่ได้จนกว่าจะ add remote
- **JAVA_HOME** ไม่ได้ตั้งใน PowerShell session — ต้อง set เองทุกครั้ง (หรือใส่ใน `$PROFILE`)
- **Working tree:** clean ยกเว้น `assets/chiangmai-restaurants-final.json` (untracked, 248 KB)

---

## 🔴 P0 · FoursquareClient.kt — wire format fix

**ไฟล์:** `android/app/src/main/java/com/thiengkin/data/remote/FoursquareClient.kt`

Importer parse FSQ v3 ถูกต้องแล้ว (commit `4bb80b6`) แต่ Client ยังส่ง request แบบ v2 → server ไม่รู้จัก → 401/empty

| # | Bug | Current (v2) | Should be (v3) |
|---|-----|--------------|----------------|
| 1 | Base URL | `https://api.foursquare.com/v3/places/search` | `https://places-api.foursquare.com/places/search` |
| 2 | Auth header | `Authorization: <key>` | `Authorization: Bearer <key>` |
| 3 | Version header | (none) | `X-Places-Api-Version: 2025-06-17` |
| 4 | Query strategy | `categories=13065` | `query=<text>` + `sort=RELEVANCE` (FSQ v3 ignore `categories`) |
| 5 | Accept header | `Accept: application/json` | OK — keep |
| 6 | Pagination | (none) | `offset=<n>` ได้ (optional — เพิ่มทีหลัง) |

**Reference:** copy shape จาก `scripts/setup-chiangmai.mjs:21-54` (function `searchFoursquare`) — ทำงานได้แล้ว 257 places

**Test:**
1. ตั้ง `FOURSQUARE_API_KEY` ใน `BuildConfig` (key ใช้ได้อยู่แล้ว — `YXKDFN2YWJWBKMEBZB10IQ5SBL4GTSR0MO0SQSPYSNLY3YDD`)
2. Run `RestaurantRepository.refreshCity()` กับ Chiang Mai
3. Log ต้องเห็น `FSQ saved: <N> records for cm` (N > 0)

**Estimate:** ~30 นาที (1 file, 1 import shape)

---

## 🟡 P1 · Chiang Mai data → multi-city

**Background:** commit `e7c1b23` ลบ Chiang Mai data ออกจาก assets เพื่อทำ multi-city (Phase 1.5) — แต่ตอนนี้มี `data/chiangmai-restaurants-final.json` (292 places: 35 manual + 257 FSQ, 248 KB) รออยู่

**ตัดสินใจ:**
- [ ] **Option A:** Bundle เป็น `assets/seed-chiangmai.json` → import เฉพาะเมื่อ user เลือกเชียงใหม่ (ตาม Phase 1.5 design)
- [ ] **Option B:** ใช้ remote pipeline เท่านั้น (รอ FSQ Client fix ก่อน แล้ว refresh เข้า Room cache)
- [ ] **Option C:** Manual seed 35 ร้าน → `assets/seed-chiangmai.json`; FSQ 257 → remote refresh

**ต้องเช็ค:**
- `Restaurant.kt` มี field `cityId` แล้ว (Phase 2) — ใช้ได้
- `RestaurantDao.observeByCity()` มีแล้ว — ใช้ได้
- `JsonImporter` อ่าน `seed-restaurants.json` เดียว — **ต้อง refactor** ถ้าจะทำ Option A/C (per-city seed)

**ไฟล์ที่เกี่ยว:**
- `data/chiangmai-restaurants-final.json` — source (gitignored)
- `android/app/src/main/assets/seed-restaurants.json` — currently empty (185 bytes)
- `android/app/src/main/java/com/thiengkin/data/JsonImporter.kt` — single-file reader, ต้อง generalize

---

## 🟢 P2 · Cleanup (optional, low priority)

- [ ] **Untracked file:** `android/app/src/main/assets/chiangmai-restaurants-final.json` (248 KB) — working copy ที่ copy มาตอน validation ไม่ใช่ seed จริง → ลบ หรือเพิ่ม `.gitignore` rule
- [ ] **Warning 1:** `LocationRepository.kt:263` — `getFromLocation` deprecated → migrate ไป `Geocoder` API ใหม่ หรือ wrap
- [ ] **Warning 2:** `TravelHomeViewModel.kt:64` — ขาด `@OptIn(ExperimentalCoroutinesApi::class)` → เพิ่ม annotation

---

## ✅ Done recently (this session + recent)

| Date | Commit | What |
|------|--------|------|
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
| `android/app/src/main/java/com/thiengkin/data/remote/FoursquareImporter.kt` | ✅ Fixed (parses v3) |
| `android/app/src/main/java/com/thiengkin/data/remote/OsmImporter.kt` | ✅ Fixed (import) |
| `android/app/src/main/java/com/thiengkin/data/remote/FoursquareClient.kt` | ❌ Buggy (sends v2 to v3 server) |
| `C:\Users\Succubuz\.mavis\scratchpads\mvs_66c806b4008448fea77b747bff463b56\scratchpad.md` | This session's scratchpad (cross-session notes) |
