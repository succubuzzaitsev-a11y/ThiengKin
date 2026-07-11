package com.thiengkin.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Restaurant — Room entity
 *
 * Schema matches seed-restaurants.json (assets/)
 * Field names use snake_case in DB (matching JSON), camelCase in Kotlin.
 */
@Entity(tableName = "restaurants")
data class Restaurant(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,                        // "manual_cm_001" | "fsq_xxx"

    @ColumnInfo(name = "name")
    val name: String,                       // "ก๋วยเตี๋ยวลูกชาย"

    @ColumnInfo(name = "name_th")
    val nameTh: String? = null,             // ชื่อภาษาไทย (fallback ถ้า name เป็นอังกฤษ)

    @ColumnInfo(name = "category")
    val category: String? = null,           // "ก๋วยเตี๋ยว"

    @ColumnInfo(name = "category_slug")
    val categorySlug: String? = null,       // "noodle"

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
    val rating: Double? = null,             // 4.7

    @ColumnInfo(name = "review_count")
    val reviewCount: Int? = null,           // 850

    @ColumnInfo(name = "price")
    val price: Int? = null,                 // 1-4 (Foursquare price tier)

    @ColumnInfo(name = "tags")
    val tags: List<String> = emptyList(),   // ["local_favorite", "morning", "noodle"]

    @ColumnInfo(name = "source")
    val source: String,                     // "manual" | "foursquare"

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,        // user toggle (Phase 1: local only)

    @ColumnInfo(name = "photo_url")
    val photoUrl: String? = null,           // future: Foursquare photos

    @ColumnInfo(name = "menu_text")
    val menuText: String? = null,           // future: เมนูเด่น (จากรีวิว)

    @ColumnInfo(name = "ai_summary")
    val aiSummary: String? = null,          // future: AI Review Summary
)
