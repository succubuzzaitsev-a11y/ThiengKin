# เที่ยงกิน — Android App

> Android client (Kotlin + Jetpack Compose)
> **Status:** Planning — ยังไม่ได้เริ่มเขียน

---

## 📋 Planned Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 2.0+ |
| **UI** | Jetpack Compose + Material Design 3 |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 35 (Android 15) |
| **Build** | Gradle (Kotlin DSL) |
| **DI** | Hilt (later) |
| **DB** | Room 2.6+ with KSP |
| **Map** | Google Maps Compose |
| **Network** | Ktor (Phase 2 — Edge Function) |
| **Navigation** | Compose Navigation 2.8+ |

---

## 📁 Planned Structure

```
android/
├── settings.gradle.kts
├── build.gradle.kts (root)
├── gradle.properties
├── gradle/
│   └── libs.versions.toml          (version catalog)
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/tiankin/
│           │   ├── TiankinApplication.kt
│           │   ├── MainActivity.kt
│           │   ├── ui/
│           │   │   ├── theme/
│           │   │   │   ├── Color.kt
│           │   │   │   ├── Type.kt
│           │   │   │   ├── Theme.kt
│           │   │   │   └── Spacing.kt
│           │   │   ├── components/
│           │   │   │   ├── RestaurantCard.kt
│           │   │   │   ├── FilterChip.kt
│           │   │   │   ├── QuickPreset.kt
│           │   │   │   └── Skeleton.kt
│           │   │   └── screens/
│           │   │       ├── travel/
│           │   │       │   ├── TravelHomeScreen.kt
│           │   │       │   ├── RouteResultScreen.kt
│           │   │       │   └── RestaurantDetailScreen.kt
│           │   │       └── nearme/
│           │   │           ├── NearMeFilterScreen.kt
│           │   │           └── NearMeResultScreen.kt
│           │   ├── data/
│           │   │   ├── db/
│           │   │   │   ├── AppDatabase.kt
│           │   │   │   ├── RestaurantEntity.kt
│           │   │   │   ├── RestaurantDao.kt
│           │   │   │   ├── CorridorCacheEntity.kt
│           │   │   │   └── CorridorCacheDao.kt
│           │   │   ├── remote/  (Phase 2)
│           │   │   │   └── SupabaseClient.kt
│           │   │   └── repository/
│           │   │       └── RestaurantRepository.kt
│           │   ├── domain/
│           │   │   ├── model/
│           │   │   │   └── Restaurant.kt
│           │   │   ├── ranking/
│           │   │   │   └── AIRanking.kt
│           │   │   └── usecase/
│           │   │       ├── SearchNearbyUseCase.kt
│           │   │       └── GetRouteStopsUseCase.kt
│           │   └── util/
│           │       ├── Haversine.kt
│           │       ├── LocationUtils.kt
│           │       └── PermissionUtils.kt
│           └── res/
│               ├── values/
│               │   ├── colors.xml
│               │   ├── strings.xml
│               │   └── themes.xml
│               └── drawable/
```

---

## 🎯 AI Ranking Formula

```kotlin
fun calculateScore(restaurant: Restaurant, userLocation: LatLng, mode: Mode): Int {
    val ratingWeight = 0.40  // ⭐ คะแนน
    val reviewWeight = 0.30  // 👥 จำนวนรีวิว
    val distanceWeight = 0.15  // 📍 ระยะทาง
    val openWeight = 0.10  // 🟢 เปิดอยู่
    val categoryWeight = 0.05  // 🍜 ตรงประเภท

    val ratingScore = (restaurant.rating / 5.0) * 100
    val reviewScore = min(restaurant.reviewCount / 1000.0, 1.0) * 100
    val distanceScore = max(0, 100 - (distance(restaurant, userLocation) / 100))
    val openScore = if (restaurant.isOpen) 100 else 0
    val categoryScore = if (restaurant.category in selectedCategories) 100 else 50

    return (ratingScore * ratingWeight +
            reviewScore * reviewWeight +
            distanceScore * distanceWeight +
            openScore * openWeight +
            categoryScore * categoryWeight).toInt()
}
```

---

## 📊 Haversine Distance

```kotlin
fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0  // km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}
```

---

## 🔐 Permissions (AndroidManifest)

```xml
<!-- GPS -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Background location (for Travel Mode auto-detect) -->
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Internet (for Google Maps + future Edge Function) -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 🎨 Design Tokens (Kotlin)

```kotlin
// ui/theme/Color.kt
val Primary = Color(0xFFC97B3F)
val Accent = Color(0xFF2F6F5E)
val BgPage = Color(0xFFFAFAF7)
val BgCard = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF14171E)
val TextSecondary = Color(0xFF3A4256)
val TextMuted = Color(0xFF6B7280)
val Border = Color(0xFFE5E0D4)

// Travel Mode (Dark)
val AppBg = Color(0xFF0A0E1A)
val AppCard = Color(0xFF161B2A)
val AppAmber = Color(0xFFFFB547)
val AppGreen = Color(0xFF4ADE80)

// ui/theme/Spacing.kt
val Spacing1 = 4.dp
val Spacing2 = 8.dp
val Spacing3 = 12.dp
val Spacing4 = 16.dp
val Spacing5 = 20.dp
val Spacing6 = 24.dp
val Spacing7 = 32.dp
val Spacing8 = 40.dp
val Spacing9 = 48.dp
val Spacing10 = 64.dp
```

---

## 📋 Week-by-Week Tasks

### Week 1 — Data Layer
- [ ] Set up `scripts/setup-chiangmai.mjs` (already done ✅)
- [ ] Get Foursquare API key
- [ ] Run script → get `chiangmai-restaurants.json`
- [ ] Write `filter-data.mjs` → call Place Details for rating
- [ ] Create Android project skeleton
- [ ] Add Room dependencies
- [ ] Write entities + DAOs

### Week 2 — Travel Mode Core
- [ ] GPS permission flow
- [ ] Auto-detect driving (speed check)
- [ ] Province picker (Chiang Mai)
- [ ] Travel Mode UI (dark)
- [ ] "จะไปไหน?" input
- [ ] Corridor query (Haversine + polyline)
- [ ] Top 5-10 stops display
- [ ] Deep link Google Maps

### Week 3 — AI + Categories
- [ ] AI ranking formula
- [ ] Categories (local Thai)
- [ ] Random button
- [ ] Quick presets
- [ ] Filter UI

### Week 4 — Rural + Offline
- [ ] "คนท้องถิ่นแนะนำ" badge
- [ ] Contextual Attributes (WiFi/ที่จอด/Pet)
- [ ] "เปิดเช้า", "ร้านริมทาง", "ของฝาก" presets
- [ ] Offline corridor cache

### Week 5 — Polish
- [ ] Favorites + Share
- [ ] User votes
- [ ] AI Personalization
- [ ] Real driving test (Bangkok → Chiang Mai)
- [ ] APK smoke test
- [ ] Ship to user

### Week 6 — Multi-province + Phase 2
- [ ] Add Province 2
- [ ] Supabase Edge Function
- [ ] Google Places integration
- [ ] Cache logic

---

## 🧪 Testing Strategy

### Unit Tests
- AI ranking formula
- Haversine distance
- Corridor logic

### Integration Tests
- Room DB queries
- Repository layer

### Manual Tests (APK smoke test)
1. Install on real device
2. Cold launch → verify splash + main screen
3. GPS permission flow (grant + deny)
4. Search near me
5. Travel Mode (mock a route)
6. Tap "Open in Maps" → verify Google Maps opens
7. Favorite a restaurant → verify saved
8. Force-stop + relaunch → verify favorites persist
9. Disable network → verify offline mode works

---

## 📚 Key References

- [Jetpack Compose Docs](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Google Maps Compose](https://developers.google.com/maps-platform/maps-compose)
- [Foursquare Places API](https://location.foursquare.com/developer/reference/places-api-overview)

---

**Status:** Planning ✅ — พร้อมเริ่ม Week 1
