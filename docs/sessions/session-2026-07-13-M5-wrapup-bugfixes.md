# Session Summary: ThiengKin M5 Wrap-up + Bug Fixes

**Date:** 2026-07-13
**Session ID:** mvs_271d59eac55f46eba492daad4702cff0
**Project:** ThiengKin (Android, ThiengTham brand)
**GitHub:** succubuzzaitsev-a11y / ThiengKin

---

## สิ่งที่ทำใน session นี้

### 1. SSH key setup — `succubuzzaitsev-a11y` account

| Item | Detail |
|------|--------|
| Private key | `~/.ssh/succubuzzaitsev_push_ed25519` (ed25519, no passphrase) |
| SSH config alias | `github.com-succubuzzaitsev` → key ใหม่ |
| Public key | `ssh-ed25519 AAAAC3... succubuzzaitsev-a11y@thiengkin` |
| Fingerprint | `SHA256:y3Yh3Z9WQO2fmnxhB0h35w3Mxnn2ifVYLu5IOjPlxmU` |
| Verified | ✅ `Hi succubuzzaitsev-a11y!` |

**ปัญหาที่เจอ:** user ต้อง login GitHub เป็น `succubuzzaitsev-a11y` (ไม่ใช่ `pornchaisic-cloud`) ถึงจะ paste key ถูก account

---

### 2. Gradle properties — Supabase API keys

| Item | Value |
|------|-------|
| URL | `https://zlntknagzrcoduzxngmx.supabase.co` |
| ANON_KEY | `sb_publishable_H1T3QAKzE49WM5yuJ9UXMg_1iqmZVvf` (publishable, RLS-enforced) |
| Secret key (ไม่ได้ใช้) | `sb_secret_<redacted>` — **bypasses RLS, ห้าม embed ใน client** (full key ใน scratchpad/chat เดิม — rotate ASAP) |
| File | `D:\thiengKin\android\gradle.properties` |
| Verified | ✅ PostgREST call สำเร็จ |

> **หมายเหตุ:** Supabase project นี้ใช้ **opaque format** (`sb_publishable_...`) ไม่ใช่ JWT (`eyJ...`) — memory เก่าต้องแก้

---

### 3. Git push (5 commits → `succubuzzaitsev-a11y/ThiengKin`)

```powershell
# Remote URL ใช้ alias → SSH key ใหม่
git remote set-url origin git@github.com-succubuzzaitsev:succubuzzaitsev-a11y/ThiengKin.git
```

| Commit | Hash | Message |
|--------|------|---------|
| chore | `d96c3f4` | ignore sweep pipeline artifacts + underscore-prefixed debug scripts |
| fix | `14e5c5d` | M5 picker UX + geography seed race + refresh cancel |
| docs | `079938e` | mark M4 + docs done; M5 in progress with clear blockers |
| docs | `8ca1c9d` | update README + CHANGELOG to v4 nationwide state |
| feat | `c9bb809` | M4 province picker finalize — GPS auto-detect, search by name, OSM-actual filter chips |

---

### 4. Bug fixes (4 issues จาก user feedback)

#### 4a. Province picker — เห็นแค่ 1 จังหวัด (นนทบุรี)

**Root cause:** `TravelHomeViewModel.kt` — v1 logic ใช้ `while (provinces.isEmpty())` exit เมื่อ province แรก insert → race condition เพราะ geography seed ยังไม่เสร็จ (77 provinces)

**Fix:** poll จนกว่าจะได้ ≥77 provinces หรือ timeout 30s

```kotlin
// TravelHomeViewModel.kt:412
const val EXPECTED_PROVINCE_COUNT = 77

// TravelHomeViewModel.kt:221
while (provinces.size < EXPECTED_PROVINCE_COUNT && attempts < 200) {
    provinces = provinceDao.getAll()
    if (provinces.size >= EXPECTED_PROVINCE_COUNT) break
    kotlinx.coroutines.delay(50)
    attempts++
}
```

#### 4b. Filter chip "ของฝาก" → "ร้านกาแฟ"

**Root cause:** label ผิด + รวม `bubble_tea` ซึ่งไม่ใช่กาแฟ

**Fix:**
```kotlin
// TravelHomeScreen.kt:209 — label
listOf("ทั้งหมด", "ริมทาง", "เปิดเช้า", "คนท้องถิ่น", "ร้านกาแฟ")

// TravelHomeViewModel.kt:454 — predicate
"ร้านกาแฟ" to { r ->
    r.category == "คาเฟ่" || r.tags.contains("cuisine:coffee_shop")
}
// ลบ bubble_tea ออก
```

#### 4c. Filter "เปิดเช้า" — filter แค่ `openingHours != null` (ผิด)

**Root cause:** เดิม filter แค่ "ร้านที่ระบุเวลา" ไม่ใช่ "เปิดเช้าจริง"

**Fix:** parse openingHours string (OSM format) หา early-morning range ก่อน 10:00
```kotlin
"เปิดเช้า" to { r ->
    val oh = r.openingHours ?: return@to false
    val timePattern = Regex("""(\d{1,2}):(\d{2})\s*[-–]\s*(\d{1,2}):(\d{2})""")
    oh.split(';').any { segment ->
        timePattern.find(segment)?.let { match ->
            val startHour = match.groupValues[1].toIntOrNull() ?: return@let false
            startHour < 10
        } ?: false
    }
}
```

#### 4d. "สรุปรีวิว (Ai)" label + typo

**Root cause:** label มี tag "(Ai)" ไม่จำเป็น + มี typo

**Fix:**
```kotlin
// RestaurantDetailScreen.kt:264
"สรุปรีวิว",  // v0.3: ลบ "(Ai)" tag

// RestaurantDetailScreen.kt:278 — typo fix
// เดิม: "ยังงมีสรุปรีวิว"
?: "ยังไม่มีสรุปรีวิว"
```

---

### 5. Build APK

| Item | Value |
|------|-------|
| APK path | `D:\thiengKin\android\app\build\outputs\apk\debug\app-debug.apk` |
| Size | 17.12 MB |
| Built | 2026-07-13 21:45 |
| Version | 0.1.0-debug |
| Package | `com.thiengkin.debug` |
| Supabase URL | ✅ `https://zlntknagzrcoduzxngmx.supabase.co` |
| Supabase Anon Key | ✅ `sb_publishable_...` (baked in BuildConfig) |
| Kotlin compile | ✅ BUILD SUCCESSFUL (no errors) |

---

## สิ่งที่ยังค้าง (P1)

- [ ] **Smoke test** บน device — province picker ควรเห็น 77 จังหวัด
- [ ] **commit `gradle.properties`** — anon key เป็น publishable ใช้ได้ แต่ต้องตัดสินใจ
- [ ] **อัพเดต TODO.md** — mark M5 done
- [ ] **อัพเดต README.md** — บันทึก Supabase data 33,442 rows nationwide
- [ ] **Rotate service_role key** — key เคยโผล่ใน chat transcript

## สิ่งที่ต้องทำ (P2)

- [ ] ลบ `android/app/src/main/assets/chiangmai-restaurants-final.json` (legacy, 248 KB)
- [ ] Fix Kotlin warning `LocationRepository.kt:263` — `getFromLocation` deprecated
- [ ] Fix Kotlin warning `TravelHomeViewModel.kt:64` — ขาด `@OptIn`

## สิ่งที่ต้องจำ

### Supabase API key naming (updated 2026-07-13)
- **New projects (2025+)** → both keys are **opaque** (`sb_publishable_...` / `sb_secret_...`)
- **Old projects** → both keys are **JWT** (`eyJ...`)
- ไม่ต้อง rotate Publishable key — เป็น public key by design
- **ต้อง** rotate Secret key ถ้าเคย leak

### SSH config
```
github.com-thiengtham        → thiengtham_push_ed25519      (pornchaisic-cloud — THIENGTHAM)
github.com                   → thiengtham_push_ed25519      (default fallback)
github.com-succubuzzaitsev   → succubuzzaitsev_push_ed25519 (succubuzzaitsev-a11y — ThiengKin)
```

### gradle build script path
```
C:\Users\Succubuz\_mavis_build.ps1  — full APK build
C:\Users\Succubuz\_mavis_check.ps1  — Kotlin compile check only
JAVA_HOME = C:\Users\Succubuz\.jdks\jbr-17.0.14
```
