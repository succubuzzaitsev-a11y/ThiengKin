# เที่ยงกิน — Android App

> Android client (Kotlin + Jetpack Compose) · **Status:** ✅ Skeleton compile-ready + debug APK builds (2026-07-11)

---

## ⚡ Quick Start

```powershell
# 1. Java JDK — ใช้ JBR ที่ Android Studio bundle มา
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;C:\Users\Succubuz\AppData\Local\Android\Sdk\platform-tools;$env:Path"

# 2. Android SDK (min SDK 24, target 34)
#    android-34 platform ต้อง install ผ่าน Android Studio → SDK Manager
#    local.properties มี sdk.dir ตั้งไว้แล้ว

# 3. Build APK debug
cd D:\thiengKin\android
.\gradlew.bat assembleDebug

# 4. Install บนอุปกรณ์
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

> **หมายเหตุ:** โปรเจกต์ใช้ `compileSdk = 34` (ไม่ใช่ 35) เพราะเครื่อง dev มีแค่ android-34/36/36.1 platform ติดตั้ง — ถ้าจะ bump เป็น 35 → ติดตั้ง android-35 ผ่าน Android Studio ก่อน แล้วแก้ `compileSdk` ใน `app/build.gradle.kts`

---

## 📁 โครงสร้าง (ตามที่ implement แล้ว)

```
android/
├── gradle/libs.versions.toml        # version catalog
├── gradle/wrapper/                  # gradle 8.9
├── app/
│   ├── build.gradle.kts             # compileSdk 35, minSdk 24
│   └── src/main/
│       ├── AndroidManifest.xml      # GPS + Internet perms
│       ├── assets/
│       │   └── chiangmai-restaurants-final.json   # 292 ร้าน
│       ├── java/com/thiengkin/
│       │   ├── ThiengKinApp.kt      # Application — init Room + Import
│       │   ├── MainActivity.kt      # Compose host + Navigation
│       │   ├── data/                # Room + JSON Importer
│       │   │   ├── Restaurant.kt
│       │   │   ├── RestaurantDao.kt
│       │   │   ├── RestaurantRepository.kt
│       │   │   ├── ThiengKinDatabase.kt
│       │   │   ├── Converters.kt
│       │   │   └── JsonImporter.kt
│       │   ├── ui/
│       │   │   ├── theme/           # Color · Type · Spacing · Theme
│       │   │   ├── components/      # Pill · FilterChip · RestaurantCard · RouteLine · SearchInput
│       │   │   └── screens/         # 7 screens ตาม MOCKUP-v3
│       │   │       ├── travel/      # TravelHome, RouteResult, RestaurantDetail
│       │   │       ├── nearme/      # NearMe
│       │   │       ├── LoadingScreen.kt
│       │   │       ├── EmptyOfflineScreen.kt
│       │   │       └── FavoritesScreen.kt
│       │   └── util/Haversine.kt
│       └── res/                     # strings, themes, font certs, launcher
```

---

## 🗺️ 7 Screens (MOCKUP-v3 mapping)

| # | Screen | Route | File | Mode |
|---|--------|-------|------|------|
| 1 | Travel Home | `travel_home` | `travel/TravelHomeScreen.kt` | Dark |
| 2 | Route Result | `route_result` | `travel/RouteResultScreen.kt` | Dark |
| 3 | Restaurant Detail | `restaurant/{id}` | `travel/RestaurantDetailScreen.kt` | Dark |
| 4 | Near-me | `near_me` | `nearme/NearMeScreen.kt` | Light |
| 5 | Loading | `loading` | `LoadingScreen.kt` | Dark |
| 6 | Empty/Offline | `empty_offline` | `EmptyOfflineScreen.kt` | Dark |
| 7 | Favorites | `favorites` | `FavoritesScreen.kt` | Light |

---

## 🎨 Design System (matches MOCKUP-v3)

| Token | Value | Use |
|-------|-------|-----|
| `Ink` | `#0F0F0F` | Primary text + dark BG |
| `Paper` | `#FAFAFA` | Light BG |
| `Red` | `#DC2626` | Brand (Thieng Tham) + CTA |
| `Mustard` | `#FACC15` | Rating + ETA |
| `Green` | `#16A34A` | "เปิดอยู่" (semantic) |
| Font | Sarabun (1 ตัว) | Google Downloadable Fonts |

ดู `ui/theme/Color.kt`, `Type.kt`, `Spacing.kt` สำหรับ full tokens

---

## 🧪 Smoke Test Checklist (ก่อน ship APK)

1. `adb install -r app-debug.apk` → install สำเร็จ
2. Cold launch → splash + Travel Home render
3. JSON import → ตรวจ `adb logcat | grep JsonImporter` ต้องเห็น "Imported 292 restaurants"
4. Tap restaurant → Restaurant Detail เปิด
5. Tap ❤️ favorite → สีเปลี่ยน → kill app + relaunch → favorite persist
6. Tap "นำทาง" → Google Maps deep link เปิด
7. Force-stop + relaunch → data ยังอยู่ (Room persist)

---

## 📚 Key Files

- **Data source:** `app/src/main/assets/chiangmai-restaurants-final.json` (292 ร้าน)
- **Schema:** `data/Restaurant.kt` (Room entity)
- **Design source of truth:** `../docs/MOCKUP-v3.html`
- **Architecture:** `../docs/ARCHITECTURE.md`

---

**Status:** ✅ Debug APK builds successfully (17.1 MB) · ⚠️ Runtime smoke test บน emulator/device ยังไม่เสร็จ — เน้นเช็ค JSON import + UI render ก่อน ship
