package com.thiengkin.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Restaurant — Room entity
 *
 * Schema matches seed-restaurants.json (assets/) + OSM/FSQ fields
 * Field names use snake_case in DB (matching JSON), camelCase in Kotlin.
 *
 * Phase 2: + city_id, opening_hours, capacity, source_updated_at
 * Phase 3 (M1): + province_id, district_id (M1.a additive — city_id kept for back-compat)
 */
@Entity(tableName = "restaurants")
data class Restaurant(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,                        // "manual_bkk_001" | "osm_123456" | "fsq_abc"

    @ColumnInfo(name = "name")
    val name: String,                       // "ก๋วยเตี๋ยวลูกชาย"

    @ColumnInfo(name = "name_th")
    val nameTh: String? = null,             // ชื่อภาษาไทย (fallback ถ้า name เป็นอังกฤษ)

    @ColumnInfo(name = "category")
    val category: String? = null,           // "ก๋วยเตี๋ยว"

    @ColumnInfo(name = "category_slug")
    val categorySlug: String? = null,       // "noodle" (mapped from OSM cuisine)

    @ColumnInfo(name = "lat")
    val lat: Double,

    @ColumnInfo(name = "lng")
    val lng: Double,

    @ColumnInfo(name = "address")
    val address: String? = null,            // "ถนนพระปกเกล้า ตำบลพระสิงห์"

    @ColumnInfo(name = "district")
    val district: String? = null,           // "Mueang District" | etc.

    @ColumnInfo(name = "province")
    val province: String? = null,           // "Province" | etc.

    @ColumnInfo(name = "tel")
    val tel: String? = null,

    @ColumnInfo(name = "website")
    val website: String? = null,

    @ColumnInfo(name = "rating")
    val rating: Double? = null,             // 4.7 (FSQ Premium = null, manual = filled)

    @ColumnInfo(name = "review_count")
    val reviewCount: Int? = null,           // 850 (FSQ Premium = null)

    @ColumnInfo(name = "price")
    val price: Int? = null,                 // 1-4 (Foursquare price tier)

    @ColumnInfo(name = "tags")
    val tags: List<String> = emptyList(),   // ["local_favorite", "morning", "noodle"] + OSM amenity tags

    @ColumnInfo(name = "source")
    val source: String,                     // "manual" | "osm" | "foursquare"

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,        // user toggle (Phase 1: local only)

    @ColumnInfo(name = "photo_url")
    val photoUrl: String? = null,           // future: Foursquare photos / OSM image

    @ColumnInfo(name = "menu_text")
    val menuText: String? = null,           // future: เมนูเด่น (จากรีวิว)

    @ColumnInfo(name = "ai_summary")
    val aiSummary: String? = null,          // future: AI Review Summary

    // === Phase 2 fields (OSM data) ===

    @ColumnInfo(name = "city_id", defaultValue = "")
    val cityId: String = "",                // "bkk" | "cm" | etc. — legacy (Phase 1.5) — เก็บไว้เพื่อ back-compat
                                            // ใช้ provinceId + districtId แทน (M1 onward)

    @ColumnInfo(name = "province_id", defaultValue = "")
    val provinceId: String = "",            // "bangkok" | "chiang_mai" — Province.id (M1+)

    @ColumnInfo(name = "district_id")
    val districtId: String? = null,         // "phra_nakhon" | "amphawa" — District.id (M1+)

    @ColumnInfo(name = "opening_hours")
    val openingHours: String? = null,       // "Mo-Fr 10:00-22:00; Sa-Su 11:00-23:00" (OSM)

    @ColumnInfo(name = "capacity")
    val capacity: Int? = null,              // จำนวนที่นั่ง (OSM)

    @ColumnInfo(name = "source_updated_at")
    val sourceUpdatedAt: Long? = null,      // timestamp (millis) — ใช้เช็ค cache age
)
