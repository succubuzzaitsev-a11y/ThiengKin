package com.thiengkin.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * GeographyRepository — seed provinces + districts tables จาก `assets/thailand-geography.json`
 *
 * เรียกครั้งเดียวตอน first launch (เช็ค province count) — ถ้ามีอยู่แล้ว skip
 *
 * ทำไม bundle ในแอป:
 * - Phase 1: ไม่ต้องการ auth/network
 * - 77+928 records เล็ก (~500KB JSON) เหมาะ bundle
 * - ใช้ทันทีตอน first launch (ไม่ต้องรอ Supabase)
 *
 * Phase 2 (M2): เปลี่ยนเป็น sync จาก Supabase `provinces` + `districts` table
 */
class GeographyRepository(
    private val context: Context,
    private val provinceDao: ProvinceDao,
    private val districtDao: DistrictDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun importIfEmpty(): ImportResult = withContext(Dispatchers.IO) {
        val existingProvinces = provinceDao.count()
        if (existingProvinces > 0) {
            val existingDistricts = districtDao.count()
            Log.i(
                TAG,
                "Geography already seeded: $existingProvinces provinces, $existingDistricts districts — skip",
            )
            return@withContext ImportResult(
                skipped = true,
                provinces = existingProvinces,
                districts = existingDistricts,
            )
        }

        try {
            val raw = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            val file = json.decodeFromString<ThailandGeographyFile>(raw)

            // Insert provinces ก่อน — districts ไม่มี FK constraint แต่ logic ต้องการ
            val provinces = file.provinces.map { it.toEntity() }
            provinceDao.insertAll(provinces)

            val districts = file.districts.map { it.toEntity() }
            districtDao.insertAll(districts)

            Log.i(TAG, "Geography seeded: ${provinces.size} provinces + ${districts.size} districts")
            ImportResult(
                skipped = false,
                provinces = provinces.size,
                districts = districts.size,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Geography import failed", e)
            ImportResult(skipped = false, provinces = 0, districts = 0, error = e.message)
        }
    }

    private fun ProvinceDto.toEntity() = Province(
        id = id,
        code = code,
        nameTh = nameTh,
        nameEn = nameEn,
        regionId = regionNesdb,
        centroidLat = centroid.lat,
        centroidLng = centroid.lng,
        bboxS = bbox.s,
        bboxW = bbox.w,
        bboxN = bbox.n,
        bboxE = bbox.e,
        areaSqkm = areaSqkm,
    )

    private fun DistrictDto.toEntity() = District(
        id = id,
        code = code,
        provinceId = provinceId,
        nameTh = nameTh,
        nameEn = nameEn,
        centroidLat = centroid.lat,
        centroidLng = centroid.lng,
        bboxS = bbox.s,
        bboxW = bbox.w,
        bboxN = bbox.n,
        bboxE = bbox.e,
        areaSqkm = areaSqkm,
    )

    data class ImportResult(
        val skipped: Boolean,
        val provinces: Int,
        val districts: Int,
        val error: String? = null,
    )

    companion object {
        private const val TAG = "GeographyRepository"

        /** Bundled JSON — copied from `data/thailand-geography.json` (M0 deliverable) */
        const val ASSET_FILE = "thailand-geography.json"
    }
}

/**
 * Convert a [Province] to [BoundingBox] for Overpass API queries.
 * Province.bboxS/W/N/E maps 1:1 to BoundingBox.south/west/north/east.
 */
fun Province.toBoundingBox(): BoundingBox =
    BoundingBox(south = bboxS, west = bboxW, north = bboxN, east = bboxE)

/** Same for [District] — ใช้ตอน drill-down ในจังหวัดเดียว */
fun District.toBoundingBox(): BoundingBox =
    BoundingBox(south = bboxS, west = bboxW, north = bboxN, east = bboxE)
