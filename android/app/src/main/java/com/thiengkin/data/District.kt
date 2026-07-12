package com.thiengkin.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * District — อำเภอ/เขต (928 อำเภอทั่วประเทศ)
 *
 * Data source: `assets/thailand-geography.json` (chingchai/OpenGISData-Thailand)
 * Seed: [GeographyRepository.importIfEmpty] runs on first launch.
 *
 * Schema:
 * - `id` = slug (e.g. "phra_nakhon", "amphawa")
 * - `code` = official 4-digit code (e.g. "1001" for Phra Nakhon, Bangkok)
 * - `provinceId` = FK to Province.id (slug, e.g. "bangkok")
 *
 * Index on `province_id` — ใช้บ่อยตอน load districts ของ province ที่เลือก
 */
@Entity(
    tableName = "districts",
    indices = [Index("province_id")],
)
data class District(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,                        // "phra_nakhon" | "amphawa" | ...

    @ColumnInfo(name = "code")
    val code: String,                      // "1001" | "7503" | ... (4-digit: 2-digit province + 2-digit district)

    @ColumnInfo(name = "province_id")
    val provinceId: String,                // "bangkok" (FK → Province.id)

    @ColumnInfo(name = "name_th")
    val nameTh: String,                    // "พระนคร"

    @ColumnInfo(name = "name_en")
    val nameEn: String,                    // "Phra Nakhon"

    @ColumnInfo(name = "centroid_lat")
    val centroidLat: Double,

    @ColumnInfo(name = "centroid_lng")
    val centroidLng: Double,

    @ColumnInfo(name = "bbox_s")
    val bboxS: Double,

    @ColumnInfo(name = "bbox_w")
    val bboxW: Double,

    @ColumnInfo(name = "bbox_n")
    val bboxN: Double,

    @ColumnInfo(name = "bbox_e")
    val bboxE: Double,

    @ColumnInfo(name = "area_sqkm")
    val areaSqkm: Double? = null,
)
