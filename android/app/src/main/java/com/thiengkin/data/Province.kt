package com.thiengkin.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Province — จังหวัด (77 จังหวัดทั่วประเทศ)
 *
 * Data source: `assets/thailand-geography.json` (chingchai/OpenGISData-Thailand)
 * Seed: [GeographyRepository.importIfEmpty] runs on first launch.
 *
 * Schema:
 * - `id` = slug (e.g. "bangkok", "chiang_mai") — used in Restaurant.provinceId
 * - `code` = official 2-digit code (e.g. "10", "50") — for future Supabase / Foursquare lookups
 * - `regionId` = NESDB region ("central" | "north" | etc.) — future filtering
 * - `bbox` = bounding box (s/w/n/e) for Overpass API query
 * - `centroid` = center lat/lng — used for distance sort fallback
 *
 * Phase A (M1–M5): primary filter dimension
 * Phase B: leaderboard (visited provinces), tier system
 */
@Entity(tableName = "provinces")
data class Province(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,                        // "bangkok" | "chiang_mai" | "phuket" | ...

    @ColumnInfo(name = "code")
    val code: String,                      // "10" | "50" | "83" | ... (official 2-digit code)

    @ColumnInfo(name = "name_th")
    val nameTh: String,                    // "กรุงเทพมหานคร"

    @ColumnInfo(name = "name_en")
    val nameEn: String,                    // "Bangkok"

    @ColumnInfo(name = "region_id")
    val regionId: String? = null,          // "central" (NESDB region)

    @ColumnInfo(name = "centroid_lat")
    val centroidLat: Double,

    @ColumnInfo(name = "centroid_lng")
    val centroidLng: Double,

    @ColumnInfo(name = "bbox_s")
    val bboxS: Double,                     // south

    @ColumnInfo(name = "bbox_w")
    val bboxW: Double,                     // west

    @ColumnInfo(name = "bbox_n")
    val bboxN: Double,                     // north

    @ColumnInfo(name = "bbox_e")
    val bboxE: Double,                     // east

    @ColumnInfo(name = "area_sqkm")
    val areaSqkm: Double? = null,
)
