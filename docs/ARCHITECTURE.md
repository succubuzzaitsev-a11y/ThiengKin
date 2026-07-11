# เที่ยงกิน — Architecture

> Tech stack · Data flow · Decisions · Trade-offs

---

## 🏗️ Tech Stack

### Phase 1 (MVP)

| Layer | Technology | Why |
|-------|------------|-----|
| **Mobile** | Android (Kotlin + Jetpack Compose) | User มีประสบการณ์ · Material Design 3 native |
| **Local DB** | Room (SQLite) | Offline-first · เร็ว · ไม่ต้อง server |
| **Data** | Foursquare Places API v3 | ฟรี 100K/เดือน · ไม่ต้องใส่บัตร |
| **Map UI** | Google Maps SDK | ฟรี · ครอบคลุมไทยดี · deep link ง่าย |
| **Setup** | Node.js 18+ (ES modules) | Built-in fetch · ไม่ต้อง deps |

### Phase 2 (Production)

| Layer | Technology | Why |
|-------|------------|-----|
| **Backend** | Supabase Edge Function (Deno) | API key เก็บใน Edge ไม่อยู่ใน APK |
| **DB Cache** | Supabase Postgres | Cache search results · 1-3 hr TTL |
| **Data** | Google Places API (New) | ข้อมูลครบ · ร้านเล็กร้านซอย · $200 free/mo |
| **AI** | Gemini 1.5 Flash (free tier) | 1,500 req/วัน · สำหรับ Review Summary + Chat |
| **Sync** | Supabase Realtime | favorites sync ระหว่าง 2 เครื่อง |

---

## 📊 Data Flow Detail

### Phase 1: Setup Pipeline

```
[Manual: สมัคร Foursquare API]
            ↓
[Setup script run ครั้งเดียว]
   ├─ Foursquare API (paginated, max 200 results)
   ├─ Transform: clean + categorize
   └─ Output: chiangmai-restaurants.json
            ↓
[Filter script (TODO)]
   ├─ เรียก Place Details สำหรับ rating
   ├─ Filter: rating ≥ 4.0, has reviews
   └─ Output: chiangmai-restaurants-filtered.json
            ↓
[Android: แปลง JSON → Kotlin objects]
            ↓
[Room DB INSERT]
```

### Phase 1: Runtime Query (Travel Mode)

```
[User input: "จะไปไหน?"]
            ↓
[Geocode destination]  ← Google Maps Geocoding API (free)
            ↓
[Compute route corridor]
   ├─ Origin: current GPS
   ├─ Destination: user input
   ├─ Get route polyline from Google Directions
   └─ Buffer 20 km around polyline
            ↓
[Query Room DB: restaurants IN corridor]
   ├─ Distance check (point-to-polyline)
   ├─ Apply filter (open now, rating)
   └─ Order by distance to route
            ↓
[AI Ranking formula: 40/30/15/10/5]
            ↓
[Top 5-10 results + ETA + detour]
            ↓
[User click → Deep link Google Maps]
```

### Phase 2: Cache + Edge Function

```
[App request: search near (lat, lng, radius)]
            ↓
[Edge Function: places-search]
   ├─ Auth check (Supabase anon key)
   ├─ Rate limit (per IP)
   ├─ Check Supabase cache (1hr TTL)
   │   ├─ Fresh → Return cached
   │   └─ Stale → Call Google Places
   ├─ Call Google Places API
   ├─ Save to cache
   └─ Return JSON
            ↓
[App: AI ranking on device]
            ↓
[Display results]
```

---

## 🔑 Key Decisions & Rationale

### Why Android-only first?

- ✅ User มีประสบการณ์ Android + Kotlin (จาก FB AutoPost)
- ✅ Material Design 3 + Compose = pro UI ทำได้เร็ว
- ✅ iOS = Phase 4+ (ถ้ามี demand)
- ✅ 80% ของ user ไทยใช้ Android

### Why Local-First (not Supabase from day 1)?

- ✅ **Cost:** $0 ตลอดกาล
- ✅ **Speed:** <100ms query vs 200-500ms API call
- ✅ **Offline:** ใช้ได้บนทางด่วน (เน็ตไม่มี)
- ✅ **Privacy:** ไม่ track ทุก search
- ⚠️ Trade-off: ต้อง refresh data เอง (ไม่ real-time)
- Mitigation: Setup script run weekly + push to Room

### Why Foursquare for Phase 1, not Google Places?

| | Foursquare | Google Places |
|---|-----------|---------------|
| Credit card | ❌ ไม่ต้อง | ✅ ต้อง |
| Cost | $0 (100K/mo) | $0 แรก แล้วจ่ายต่อ |
| Setup friction | ต่ำ | สูง |
| Data quality เชียงใหม์ | พอใช้ (~100 ร้าน) | ดีกว่า |
| Decision | ✅ เริ่ม Foursquare | เก็บไว้ Phase 2 |

### Why on-device AI ranking, not cloud?

- ✅ **Cost:** $0 (vs $X/1K queries)
- ✅ **Latency:** <10ms (vs 200-500ms round trip)
- ✅ **Privacy:** search ไม่หลุด server
- ✅ **Offline:** ทำงานได้แม้ไม่มีเน็ต
- ⚠️ Trade-off: ไม่ใช่ LLM (rule-based formula 40/30/15/10/5)
- Mitigation: Phase 2 เพิ่ม Gemini Flash สำหรับ "AI summary" เท่านั้น

### Why API key in Edge Function, not in app?

- 🔒 **Security:** APK reverse-engineer หา key ไม่เจอ
- 🔒 **Rotation:** เปลี่ยน key ได้โดยไม่ต้องอัพเดตแอป
- 🔒 **Rate limit:** คุมจาก server ได้
- 🔒 **Cost control:** ตั้ง budget alert ที่ Google Cloud

---

## 🗄️ Room DB Schema (Draft)

```kotlin
@Entity(tableName = "restaurants")
data class Restaurant(
    @PrimaryKey val id: String,              // Foursquare fsq_id
    val name: String,
    val nameTh: String?,
    val category: String,
    val categorySlug: String,
    val lat: Double,
    val lng: Double,
    val address: String,
    val district: String,
    val province: String,
    val tel: String?,
    val website: String?,
    val hours: String?,
    val rating: Float?,                      // 0.0 - 5.0
    val reviewCount: Int,
    val priceLevel: Int?,                    // 1-4
    val tags: String,                        // comma-separated
    val isFavorite: Boolean = false,
    val fetchedAt: Long                      // timestamp
)

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val restaurantId: String,
    val addedAt: Long
)

@Entity(tableName = "routes")
data class Route(
    @PrimaryKey @Generated val id: Long,
    val name: String,                        // "กรุงเทพ → เชียงใหม์"
    val origin: String,                      // JSON: {lat, lng, name}
    val destination: String,                 // JSON
    val polyline: String,                    // encoded polyline
    val lastUsed: Long
)

@Entity(tableName = "corridor_cache")
data class CorridorCache(
    @PrimaryKey val routeKey: String,        // hash(origin+dest+radius)
    val restaurantIds: String,               // comma-separated IDs
    val cachedAt: Long,
    val expiresAt: Long
)
```

---

## 💰 Cost Model (Detailed)

### Phase 1 (Local-First)

| Item | Cost | Calculation |
|------|------|-------------|
| Foursquare API | $0 | 100K free/month, 1-2 users = ~100 calls/month |
| Google Maps SDK | $0 | Free 28K loads/month |
| Room DB (local) | $0 | No cost |
| Setup script (Node) | $0 | Runs on user's machine |
| **Total** | **$0** | |

### Phase 2 (with Google Places)

| Item | Cost | Calculation |
|------|------|-------------|
| Google Places (New) | $0* | $200 free/month, 1-2 users = ~$0.50/month actual |
| Supabase (free tier) | $0 | 500MB DB, 2GB bandwidth |
| Gemini Flash | $0 | 1,500 req/day free |
| **Total** | **$0** (real-world) | |

### If scaled to 1,000 users

| Item | Cost |
|------|------|
| Google Places | $15-30/month |
| Supabase (Pro) | $25/month |
| Gemini Flash | ~$5/month |
| **Total** | **$45-60/month** |

---

## 🔧 Build System

### Android

- **Build:** Gradle (Kotlin DSL)
- **UI:** Jetpack Compose + Material Design 3
- **DI:** Hilt (later)
- **DB:** Room with KSP
- **Network:** Ktor (later, for Phase 2 Edge Function)
- **Maps:** Google Maps Compose
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 35 (Android 15)

### Project Structure (planned)

```
android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/java/com/tiankin/
│       ├── MainActivity.kt
│       ├── ui/
│       │   ├── theme/          (Material 3 + design tokens)
│       │   ├── screens/
│       │   │   ├── travel/
│       │   │   └── nearme/
│       │   └── components/
│       │       ├── RestaurantCard.kt
│       │       └── FilterChip.kt
│       ├── data/
│       │   ├── db/             (Room)
│       │   └── repository/
│       ├── domain/
│       │   ├── model/
│       │   └── usecase/
│       └── util/
├── build.gradle.kts
└── settings.gradle.kts
```
