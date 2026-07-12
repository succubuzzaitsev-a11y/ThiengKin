package com.thiengkin.data

/**
 * Bounding box — ใช้กับ Overpass API `query(bbox)`
 *
 * Format: (south, west, north, east) — ตาม Overpass spec
 * Example: Bangkok bbox = (13.65, 100.35, 13.90, 100.80)
 *
 * เดิมอยู่ใน [City.kt] (Phase 1.5) — ย้ายออกมาเป็นไฟล์แยกตอน M1.b (ลบ City.kt)
 * ใช้กับ [Province.toBoundingBox] / [District.toBoundingBox] (extension fn ใน GeographyRepository)
 */
data class BoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    /** Format เป็น Overpass bbox string: "south,west,north,east" */
    fun toOverpassString(): String = "$south,$west,$north,$east"

    /** พื้นที่ (ตร.กม.) โดยประมาณ — ใช้เช็คว่า bbox ใหญ่เกินไป (Overpass จะ reject > ~0.5°) */
    val areaSqKm: Double
        get() {
            val midLat = (south + north) / 2.0
            val heightKm = (north - south) * 111.0
            val widthKm = (east - west) * 111.0 * kotlin.math.cos(Math.toRadians(midLat))
            return heightKm * widthKm
        }
}
