package com.thiengkin.data

import com.thiengkin.util.Haversine

/**
 * Computed/derived properties on [Restaurant] — ไม่เก็บใน entity
 *
 * - [distanceMeters]: ระยะทางจาก user location (Phase 1.5 จะ inject ผ่าน VM)
 * - [etaMinutes]: เวลาเดินทางโดยประมาณ (60 km/h)
 * - [favoritedDaysAgo]: จำนวนวันตั้งแต่กด favorite (Phase 1.5 จะเก็บ favoritedAt)
 *
 * Phase 1 skeleton: return null → UI ใช้ fallback display ("—", "อีก 12 นาที" ฯลฯ)
 */

/** ระยะทางจาก user ปัจจุบัน (เมตร). Phase 1.5 จะคำนวณจาก user location. */
internal var userLat: Double? = null
internal var userLng: Double? = null

val Restaurant.distanceMeters: Int?
    get() {
        val ulat = userLat ?: return null
        val ulng = userLng ?: return null
        return (Haversine.distanceKm(ulat, ulng, lat, lng) * 1000).toInt()
    }

/** ETA แบบขับรถ ~60 km/h. Phase 1.5 จะใช้ Google Directions API แทน. */
val Restaurant.etaMinutes: Int?
    get() {
        val meters = distanceMeters ?: return null
        return (meters / 1000.0 / 60.0 * 60).toInt()  // 60 km/h → minutes
    }

/** วันที่กด favorite. Phase 1.5 จะเก็บ favorited_at ใน entity. */
val Restaurant.favoritedDaysAgo: Int?
    get() = null
