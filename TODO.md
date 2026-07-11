# TODO · เที่ยงกิน

> Action items สำหรับ session ถัดไป — ลบ/complete เมื่อทำเสร็จ

**Last updated:** 2026-07-12 01:38 (Asia/Bangkok)

---

## 🔴 P0 · FoursquareClient.kt — wire format fix

**ไฟล์:** `android/app/src/main/java/com/thiengkin/data/remote/FoursquareClient.kt`

ตอนนี้ Importer parse FSQ v3 ถูกต้องแล้ว (commit `4bb80b6`) แต่ Client ยังส่ง request แบบ v2 → server ไม่รู้จัก → 401/empty

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
1. ตั้ง `FOURSQUARE_API_KEY` ใน `BuildConfig`
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

---

## 🟢 P2 · Cleanup (optional, low priority)

- [ ] **Untracked file:** `android/app/src/main/assets/chiangmai-restaurants-final.json` (248 KB) — working copy ที่ copy มาตอน validation ไม่ใช่ seed จริง → ลบ หรือเพิ่ม `.gitignore` rule
- [ ] **Warning 1:** `LocationRepository.kt:263` — `getFromLocation` deprecated → migrate ไป `Geocoder` API ใหม่ หรือ wrap
- [ ] **Warning 2:** `TravelHomeViewModel.kt:64` — ขาด `@OptIn(ExperimentalCoroutinesApi::class)` → เพิ่ม annotation

---

## ✅ Done recently

| Date | Commit | What |
|------|--------|------|
| 2026-07-12 | `a006ca2` | fix(android): add missing contentOrNull import in OsmImporter (build was broken) |
| 2026-07-12 | `4bb80b6` | fix(android): FoursquareImporter parse FSQ v3 response format (was returning 0 results) |
| 2026-07-12 | (verify) | `gradle compileDebugKotlin` → SUCCESSFUL in 42s |
| 2026-07-11 | (data) | Chiang Mai data pipeline: 292 places merged, 100% in bounds |
