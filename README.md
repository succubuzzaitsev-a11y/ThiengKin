# เที่ยงกิน (ThiengKin)

> **"เปิดแอปก็เจอร้าน"** — เพื่อนร่วมทางสายกิน
> แอป Android หาร้านอร่อยข้างทาง + near-me mode · ครอบคลุม **77 จังหวัดทั่วประเทศ**
> ธีม: 🔴 แดง `#DC2626` + 🟡 เหลือง `#FACC15` (จากโลโก้ Thieng Tham Development)

---

## 📣 Tagline (เลือกใช้)

| Version | ใช้ตอนไหน |
|---------|----------|
| **"เปิดแอปก็เจอร้าน"** ← แนะนำ | One-liner, App Store, splash screen |
| "กินดี ตลอดทาง" | Marketing, social media |
| "เพื่อนร่วมทางสายกิน" | Tagline ยาว, branding |

---

## 🎯 จุดเด่น 5 ข้อ

| # | จุดเด่น | ทำไม |
|---|--------|------|
| 1 | 🚗 **Travel Mode** (Killer) | ใส่จุดหมาย → ได้ 5-10 จุดแวะ + ETA + detour (ไม่มีใครทำในไทย) |
| 2 | ✅ **"ต้องเจอ" guarantee** | ไม่มี empty state — ขยายรัศมีอัตโนมัติ |
| 3 | 🗺️ **Nationwide** | 77 จังหวัด · 928 อำเภอ · 33,442+ ร้าน (OSM data) |
| 4 | 🏘️ **ต่างจังหวัด First** | "คนท้องถิ่นแนะนำ" + ร้านริมทาง + ของฝาก (Wongnai เน้นกรุงเทพ) |
| 5 | 💸 **ฟรี 100% ไม่ track** | ไม่โฆษณา ไม่ track (1-2 users) · Supabase free tier |

---

## 📊 Status (2026-07-13)

| Milestone | Status | Commit |
|-----------|--------|--------|
| **M0** — Thailand province/district reference (77p/928d/7r) | ✅ DONE | `3e9a131` |
| **M1** — Android schema + UI migration (ProvincePicker) | ✅ DONE | `4f0d124` |
| **M2** — Supabase setup (geography push, RLS) | ✅ DONE | `be810bf` |
| **M3** — OSM nationwide pipeline (33,442 rows) | ✅ DONE | `8421e94` |
| **M4** — Province picker finalize (GPS + search + filters) | ✅ DONE | `e73678a` |
| **M5** — Ship MVP (APK smoke test + anon key + docs) | 🔄 IN PROGRESS | — |
| **Phase B** — Auth + Reviews + Points + Leaderboard | ⏳ Planned | — |

**Data state:** 33,442 OSM rows in Supabase (Bangkok 16,731 + 76 จังหวัด)

---

## 🏗️ Architecture

```
📱 Android UI (Kotlin + Compose)
   ├─ 🚗 Travel Mode (Dark)  — primary
   ├─ 📍 Near-me Mode (Light)
   └─ ProvincePicker + GPS auto-detect + Search + Filters

🗄️ Room (SQLite) — local cache
   ├─ restaurants (33,442 rows, OSM-sourced)
   ├─ provinces (77 rows, bundled from assets/)
   └─ districts (928 rows, bundled from assets/)

🌐 Supabase (Postgres + PostgREST + RLS) — remote primary
   ├─ restaurants · provinces · districts (read-only public)
   └─ 7-region, 77-province, 928-district reference data

📥 OSM Overpass API — data source
   └─ 77 จังหวัด sweep → Supabase (TTL 7 days, push script)

🤖 AI Ranking (on-device)
   └─ 40/30/15/10/5 formula · Corridor + Route-aware
```

### Data flow

```
[OSM Overpass]  →  [scripts/osm-fetch.mjs]  →  [data/osm-<province>.json]
       ↓
[scripts/osm-parse.mjs]  →  [data/parsed/osm-<province>.restaurants.json]
       ↓
[scripts/push-osm.mjs]  →  [Supabase restaurants table]
       ↓
[Android SupabaseClient]  →  [Room cache]  →  [UI]
                                                  ↓ (fallback)
                                            [OSM Overpass direct query]
```

---

## 📁 โครงสร้างโปรเจกต์

```
D:\thiengKin\
├── README.md                          ← คุณอยู่ที่นี่
├── RULES.md                           ← Standing project rules
├── TODO.md                            ← Action items + session handoff
├── docs/
│   ├── WORKFLOW.md                    ← Architecture (v4, nationwide)
│   ├── MOCKUP-v3.html                 ← UI mockup (single HTML, 7 screens)
│   ├── BEST-OF-BREED.md               ← วิเคราะห์ competitors
│   ├── ARCHITECTURE.md                ← Tech decisions + data flow
│   ├── CHANGELOG.md                   ← Version history (v1 → v4)
│   └── RESEARCH.md                    ← Market research
├── data/
│   ├── thailand-geography.json        ← 77p + 928d + 7r (committed)
│   ├── osm-*.json                     ← raw Overpass (gitignored)
│   └── parsed/osm-*.restaurants.json  ← parsed (gitignored)
├── scripts/
│   ├── osm-fetch.mjs                  ← Overpass API fetcher
│   ├── osm-parse.mjs                  ← JSON → Restaurant[]
│   ├── push-osm.mjs                   ← Supabase upsert
│   ├── sweep-osm.mjs                  ← 77-province batch runner
│   └── setup-chiangmai.mjs            ← (legacy) Foursquare Chiang Mai
├── supabase/
│   └── migrations/
│       ├── 001_initial_schema.sql     ← provinces, districts, restaurants
│       └── 002_rls_policies.sql       ← public read, anon + authenticated
└── android/                           ← Android project
    └── app/src/main/
        ├── assets/thailand-geography.json  (508 KB, bundled)
        └── java/com/thiengkin/
            ├── data/
            │   ├── local/             ← Room (Restaurant, Province, District)
            │   ├── remote/            ← SupabaseClient, OsmClient, FoursquareClient
            │   └── repository/        ← RestaurantRepository, GeographyRepository
            └── ui/
                ├── components/        ← SearchInput, ProvincePicker, RestaurantCard
                └── screens/travel/    ← TravelHomeScreen, TravelHomeViewModel
```

---

## 🎯 Quick Facts

| Item | Value |
|------|-------|
| **ชื่อแอป** | เที่ยงกิน (ThiengKin) |
| **Package** | `com.thiengkin` |
| **Platform** | Android (Kotlin + Compose + Room) |
| **Scope** | **77 จังหวัด** (nationwide) · 33,442 OSM-sourced restaurants |
| **Backend** | Supabase (PostgREST, RLS public read) + OSM Overpass fallback |
| **Data source** | OSM Overpass API (primary) · Foursquare (optional enrichment) |
| **Cost** | $0 ตลอดกาล (1-2 users, free tier ทั้งหมด) |
| **Brand** | Thieng Tham Development (บริษัทแม่) |
| **Tagline** | "เปิดแอปก็เจอร้าน" (one-liner) |
| **Theme** | 🔴 แดง `#DC2626` + 🟡 เหลือง `#FACC15` |
| **North Star** | "เปิดแอปต้องเจอร้าน" — ไม่มี empty state |

---

## 🚀 Quick Start

### Prerequisites

```powershell
# 1. Set JAVA_HOME (Android build)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 2. Set adb in PATH
$env:Path = "C:\Users\Succubuz\AppData\Local\Android\Sdk\platform-tools;$env:Path"

# 3. Verify
java -version
adb version
```

### Build APK

```powershell
cd D:\thiengKin\android
.\gradlew.bat :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk (~18 MB)
```

### Install + Run

```powershell
# Connect device or start emulator first
adb devices   # verify device shows up

# Install
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Watch logs
adb logcat -s "ThiengKinApp:*" "TravelHomeVM:*" "RestaurantRepository:*"
```

### Re-fetch OSM data (77 จังหวัด)

```powershell
cd D:\thiengKin

# Phase 1: fetch from Overpass (~15-20 min)
node scripts/sweep-osm.mjs --only fetch

# Phase 2+3: parse + push to Supabase (~10 min)
node scripts\sweep-osm.mjs --skip-fetch
```

---

## 🔑 Configuration

### Supabase

- **Project ref:** `zlntknagzrcoduzxngmx`
- **URL:** `https://zlntknagzrcoduzxngmx.supabase.co`
- **Account:** `succubuzzaitsev@gmail.com` (per GitHub boundary rules)
- **RLS:** public read (anon + authenticated), no write from client

### Android client (`gradle.properties`)

```properties
SUPABASE_URL=https://zlntknagzrcoduzxngmx.supabase.co
SUPABASE_ANON_KEY=<get from dashboard → API → Publishable key>
```

> ⚠️ **Current status:** `SUPABASE_ANON_KEY` is **empty** — Android falls back to OSM Overpass direct query (works, but less efficient). Get the key from [Supabase dashboard](https://supabase.com/dashboard/project/zlntknagzrcoduzxngmx/settings/api) → "Publishable and secret API keys" → `Publishable` (= old `anon`).

---

## 📚 เอกสาร

อ่านตามลำดับนี้:

1. **[README.md](README.md)** ← คุณอยู่ที่นี่
2. **[docs/CHANGELOG.md](docs/CHANGELOG.md)** — version history (v1 → v4 nationwide pivot)
3. **[docs/WORKFLOW.md](docs/WORKFLOW.md)** — architecture + workflow (v4)
4. **[docs/MOCKUP-v3.html](docs/MOCKUP-v3.html)** — UI mockup (7 screens, design source of truth)
5. **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — tech decisions + trade-offs
6. **[docs/BEST-OF-BREED.md](docs/BEST-OF-BREED.md)** — วิเคราะห์ Wongnai/Google/Kinraidee + สิ่งที่ยืมมา
7. **[docs/RESEARCH.md](docs/RESEARCH.md)** — market research + competitors
8. **[TODO.md](TODO.md)** — active action items + session handoff

---

## 🛣️ Roadmap

### M5 — Ship MVP (in progress)

- [ ] APK smoke test on real device (install + GPS + province picker + refresh)
- [ ] Set `BuildConfig.SUPABASE_ANON_KEY` (currently empty → Overpass fallback)
- [ ] TODO cleanup
- [ ] GitHub push (currently local-only repo)

### Phase B — After MVP ship

- **M6** — Auth (Supabase Auth, email + Google)
- **M7** — Reviews + GPS check-in
- **M8** — Points system + tier
- **M9** — Anti-cheat (report, cool-down, probation)
- **M10** — Leaderboard, badges (Explorer = visited 10 provinces)

### Backlog (lower priority)

- Travel Mode UI (dark, big buttons, route preview)
- AI ranking formula 40/30/15/10/5
- Deep link Google Maps
- English toggle
- Favorites sync (Supabase Realtime)

---

## ✅ ที่ล็อกแน่นอนแล้ว

- ✅ ชื่อ "เที่ยงกิน" — ว่าง (ไม่มีแอปชื่อนี้)
- ✅ **Nationwide scope** (77 จังหวัด · 928 อำเภอ) — ไม่ใช่ pilot 1 จังหวัด
- ✅ 2 โหมด: Travel (หลัก, dark) + Near-me (light)
- ✅ **Data source: OSM Overpass** (primary) — ไม่ใช่ Foursquare curated
- ✅ **Backend: Supabase** (Postgres + PostgREST + RLS) — anon-read
- ✅ Brand: Thieng Tham Development (บริษัทแม่)
- ✅ UI ตัด "AI" ออก — ใช้ "สรุปรีวิว (Ai)" แทน
- ✅ Cost: $0 ตลอดกาล (1-2 users)

## ❓ ที่รอตัดสินใจ

- Travel Mode ETA / route preview (OSRM vs Google Directions)
- Auth strategy: email-only vs Google Sign-In
- Points system: earn rules + tier thresholds

---

Built with ❤️ · 2026 · **33,442+ ร้าน · 77 จังหวัด · 0 บาท**
