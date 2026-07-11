package com.thiengkin.data

import android.util.Log
import com.thiengkin.data.remote.FoursquareClient
import com.thiengkin.data.remote.FoursquareException
import com.thiengkin.data.remote.FoursquareImporter
import com.thiengkin.data.remote.OsmClient
import com.thiengkin.data.remote.OsmException
import com.thiengkin.data.remote.OsmImporter
import kotlinx.coroutines.flow.Flow

/**
 * Repository — single source of truth สำหรับ Restaurant data
 *
 * Phase 1: local-only (assets → Room)
 * Phase 2: เพิ่ม remote (OSM Overpass + Foursquare Free) — fetch per city, cache in Room
 */
class RestaurantRepository(
    private val dao: RestaurantDao,
    private val osmClient: OsmClient = OsmClient(),
    private val osmImporter: OsmImporter = OsmImporter(),
    private val fsqClient: FoursquareClient? = null,  // null = skip FSQ
    private val fsqImporter: FoursquareImporter = FoursquareImporter(),
) {
    fun observeTop(limit: Int = 10): Flow<List<Restaurant>> = dao.observeTop(limit)

    fun observeAll(): Flow<List<Restaurant>> = dao.observeAll()

    fun observeManualPicks(): Flow<List<Restaurant>> = dao.observeManualPicks()

    fun observeInBoundingBox(
        minLat: Double, maxLat: Double,
        minLng: Double, maxLng: Double,
    ): Flow<List<Restaurant>> = dao.observeInBoundingBox(minLat, maxLat, minLng, maxLng)

    fun observeById(id: String): Flow<Restaurant?> = dao.observeById(id)

    fun observeFavorites(): Flow<List<Restaurant>> = dao.observeFavorites()

    /** Phase 2: ดึงร้านในเมืองที่ระบุ — manual + osm + foursquare ทั้งหมด */
    fun observeByCity(cityId: String): Flow<List<Restaurant>> = dao.observeByCity(cityId)

    suspend fun getById(id: String): Restaurant? = dao.getById(id)

    suspend fun toggleFavorite(id: String) {
        val current = dao.getById(id) ?: return
        dao.setFavorite(id, !current.isFavorite)
    }

    // === Phase 2: City data refresh ===

    /**
     * ดึงข้อมูลร้านอาหารจาก OSM Overpass + Foursquare สำหรับ city ที่ระบุ
     *
     * Cache strategy:
     * - ถ้า OSM cache อายุ < [cacheTtlMs] → skip (return CacheHit)
     * - ถ้าเก่ากว่า → ลบ cache เก่า + fetch ใหม่
     *
     * @return [RefreshResult] บอกสถานะ
     */
    suspend fun refreshCity(city: City, force: Boolean = false, cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS): RefreshResult {
        val cityId = city.id
        val bbox = city.bbox
            ?: return RefreshResult(skipped = true, reason = "City นี้ไม่รองรับ OSM fetch (no bbox)")

        val nowMs = System.currentTimeMillis()

        // === 1. OSM ===
        val osmLatest = dao.latestUpdateByCityAndSource(cityId, "osm")
        val osmCacheExpired = osmLatest == null || (nowMs - osmLatest) > cacheTtlMs

        if (force || osmCacheExpired) {
            try {
                Log.i(TAG, "OSM refresh: city=$cityId force=$force expired=$osmCacheExpired")
                val rawJson = osmClient.fetchRestaurants(bbox)
                val parsed = osmImporter.parse(rawJson, cityId, nowMs)
                if (parsed.isNotEmpty()) {
                    dao.deleteByCityAndSource(cityId, "osm")
                    dao.insertAll(parsed)
                    Log.i(TAG, "OSM saved: ${parsed.size} records for $cityId")
                }
            } catch (e: OsmException) {
                Log.w(TAG, "OSM fetch failed for $cityId: ${e.message}")
                return RefreshResult(skipped = true, reason = "OSM: ${e.message}")
            }
        } else {
            Log.d(TAG, "OSM cache hit: city=$cityId age=${nowMs - osmLatest}ms")
        }

        // === 2. Foursquare (optional — only if API key configured) ===
        if (fsqClient != null) {
            val fsqLatest = dao.latestUpdateByCityAndSource(cityId, "foursquare")
            val fsqCacheExpired = fsqLatest == null || (nowMs - fsqLatest) > FSQ_CACHE_TTL_MS

            if (force || fsqCacheExpired) {
                try {
                    Log.i(TAG, "FSQ refresh: city=$cityId force=$force expired=$fsqCacheExpired")
                    val rawJson = fsqClient.searchPlaces(bbox)
                    val parsed = fsqImporter.parse(rawJson, cityId, nowMs)
                    if (parsed.isNotEmpty()) {
                        dao.deleteByCityAndSource(cityId, "foursquare")
                        dao.insertAll(parsed)
                        Log.i(TAG, "FSQ saved: ${parsed.size} records for $cityId")
                    }
                } catch (e: FoursquareException) {
                    // FSQ failures are non-fatal (quota exhausted etc.)
                    Log.w(TAG, "FSQ fetch failed for $cityId: ${e.message}")
                }
            }
        }

        return RefreshResult(skipped = false, osmCount = dao.countByCityAndSource(cityId, "osm"))
    }

    /** Cache status สำหรับ city — ใช้แสดง "อัปเดตเมื่อ..." */
    suspend fun cacheStatus(city: City): CacheStatus {
        val cityId = city.id
        val nowMs = System.currentTimeMillis()
        val osmLatest = dao.latestUpdateByCityAndSource(cityId, "osm")
        val fsqLatest = dao.latestUpdateByCityAndSource(cityId, "foursquare")
        return CacheStatus(
            cityId = cityId,
            osmCount = dao.countByCityAndSource(cityId, "osm"),
            fsqCount = dao.countByCityAndSource(cityId, "foursquare"),
            osmUpdatedAt = osmLatest,
            fsqUpdatedAt = fsqLatest,
            nowMs = nowMs,
        )
    }

    companion object {
        private const val TAG = "RestaurantRepository"

        /** OSM cache TTL = 7 วัน (ข้อมูลไม่ค่อยเปลี่ยน, refresh บ่อยเกินจะโดน rate limit) */
        const val DEFAULT_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

        /** FSQ cache TTL = 30 วัน (free tier 500/เดืือน — ใช้ให้คุ้ม) */
        const val FSQ_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }
}

data class RefreshResult(
    val skipped: Boolean,
    val reason: String? = null,
    val osmCount: Int = 0,
)

data class CacheStatus(
    val cityId: String,
    val osmCount: Int,
    val fsqCount: Int,
    val osmUpdatedAt: Long?,
    val fsqUpdatedAt: Long?,
    val nowMs: Long,
) {
    val osmAgeMs: Long? get() = osmUpdatedAt?.let { nowMs - it }
    val fsqAgeMs: Long? get() = fsqUpdatedAt?.let { nowMs - it }
}
