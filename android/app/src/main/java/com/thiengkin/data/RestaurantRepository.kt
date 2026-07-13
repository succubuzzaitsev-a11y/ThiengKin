package com.thiengkin.data

import android.util.Log
import com.thiengkin.data.remote.FoursquareClient
import com.thiengkin.data.remote.FoursquareException
import com.thiengkin.data.remote.FoursquareImporter
import com.thiengkin.data.remote.OsmClient
import com.thiengkin.data.remote.OsmException
import com.thiengkin.data.remote.OsmImporter
import com.thiengkin.data.remote.SupabaseClient
import com.thiengkin.data.remote.SupabaseException
import com.thiengkin.data.remote.SupabaseImporter
import kotlinx.coroutines.flow.Flow

/**
 * Repository — single source of truth สำหรับ Restaurant data
 *
 * Phase 1: local-only (assets → Room)
 * Phase 2: เพิ่ม remote (OSM Overpass + Foursquare Free) — fetch per area, cache in Room
 * Phase 3 (M1): nationwide — refresh by province (or province + district)
 * Phase 3 (M3.d): OSM data มาจาก Supabase mirror (M3.c push) แทน Overpass direct
 *                 - Supabase = primary path (anon key, RLS-enforced)
 *                 - OSM direct = fallback เฉพาะกรณี Supabase ไม่มี data หรือ fail
 *                 - ทั้งคู่เขียน Room ด้วย source="osm" → cache key เดิมใช้ได้
 */
class RestaurantRepository(
    private val dao: RestaurantDao,
    private val supabaseClient: SupabaseClient? = null,        // null = disabled (no anon key)
    private val supabaseImporter: SupabaseImporter = SupabaseImporter(),
    private val osmClient: OsmClient = OsmClient(),           // fallback only
    private val osmImporter: OsmImporter = OsmImporter(),     // fallback only
    private val fsqClient: FoursquareClient? = null,          // null = skip FSQ
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

    /** Phase 3 (M1): ดึงร้านในจังหวัดที่ระบุ — manual + osm + foursquare ทั้งหมด */
    fun observeByProvince(provinceId: String): Flow<List<Restaurant>> =
        dao.observeByProvince(provinceId)

    /** Phase 3 (M1): ดึงร้านในอำเภอเฉพาะ (drill-down จาก province) */
    fun observeByDistrict(districtId: String): Flow<List<Restaurant>> =
        dao.observeByDistrict(districtId)

    suspend fun getById(id: String): Restaurant? = dao.getById(id)

    suspend fun toggleFavorite(id: String) {
        val current = dao.getById(id) ?: return
        dao.setFavorite(id, !current.isFavorite)
    }

    // === Phase 3 (M1): Province-scoped refresh (nationwide) ===

    /**
     * Generic refresh — ดึงข้อมูลร้านอาหารจาก Supabase mirror (primary) + Foursquare (optional)
     *
     * M3.d pipeline (post-Supabase mirror):
     *   1. **Supabase** (PostgREST) → primary OSM source
     *      - อ่าน `restaurants` table filtered by (province_id, source='osm')
     *      - ใช้ anon/Publishable key (RLS-enforced, safe to ship in client)
     *      - Cache: 7 วัน (same TTL as before)
     *   2. **OSM Overpass** (direct) → fallback ถ้า Supabase ไม่มี data หรือ fail
     *      - ใช้เฉพาะกรณี Supabase empty/error → กัน user ติดค้างถ้า Supabase outage
     *   3. **Foursquare** (optional) → enrichment, ไม่กระทบถ้าไม่มี key
     *
     * Cache strategy:
     *   - Cache key = (province_id, source='osm') — เหมือนเดิม, Room schema ไม่เปลี่ยน
     *   - ถ้า cache age < [cacheTtlMs] → skip fetch (ทั้ง Supabase และ Overpass)
     *   - ถ้าเก่ากว่า/force → ลบ cache เก่า + fetch ใหม่
     *
     * @param provinceId Province.id (เช่น "bangkok", "chiang_mai") — ใช้ filter + cache invalidation
     * @param districtId District.id (เช่น "phra_nakhon") — optional, drill-down filter
     * @param bbox Overpass query bbox — ใช้กรณี fallback ไป Overpass (Supabase ไม่ใช้ bbox)
     *
     * M3.d: เปลี่ยน primary path จาก Overpass → Supabase. Behavior เดิม + resilience ขึ้น.
     */
    suspend fun refreshArea(
        provinceId: String,
        districtId: String? = null,
        bbox: BoundingBox,
        force: Boolean = false,
        cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    ): RefreshResult {
        val nowMs = System.currentTimeMillis()

        // === 1. OSM (Supabase primary, Overpass fallback) ===
        val osmLatest = dao.latestUpdateByProvinceAndSource(provinceId, "osm")
        val osmCacheExpired = osmLatest == null || (nowMs - osmLatest) > cacheTtlMs

        if (force || osmCacheExpired) {
            Log.i(
                TAG,
                "OSM refresh: province=$provinceId district=$districtId force=$force expired=$osmCacheExpired source=supabase-or-overpass",
            )

            // 1a. Try Supabase first
            var supabaseOk = false
            if (supabaseClient != null) {
                try {
                    val rawJson = supabaseClient.fetchRestaurantsByProvince(provinceId, districtId)
                    val parsed = supabaseImporter.parse(rawJson, provinceId, districtId, nowMs)
                    if (parsed.isNotEmpty()) {
                        dao.deleteByProvinceAndSource(provinceId, "osm")
                        dao.insertAll(parsed)
                        Log.i(TAG, "Supabase saved: ${parsed.size} records for $provinceId (district=$districtId)")
                        supabaseOk = true
                    } else {
                        Log.w(TAG, "Supabase returned 0 rows for $provinceId/$districtId — falling back to Overpass")
                    }
                } catch (e: SupabaseException) {
                    Log.w(TAG, "Supabase fetch failed for $provinceId/${districtId ?: "(province)"}: ${e.message}")
                }
            } else {
                Log.d(TAG, "Supabase client disabled (no anon key) — going straight to Overpass")
            }

            // 1b. Overpass fallback (only if Supabase returned nothing or is disabled)
            if (!supabaseOk) {
                try {
                    val rawJson = osmClient.fetchRestaurants(bbox)
                    val parsed = osmImporter.parse(rawJson, provinceId, districtId, nowMs)
                    if (parsed.isNotEmpty()) {
                        dao.deleteByProvinceAndSource(provinceId, "osm")
                        dao.insertAll(parsed)
                        Log.i(TAG, "Overpass fallback saved: ${parsed.size} records for $provinceId")
                    }
                } catch (e: OsmException) {
                    Log.w(TAG, "Overpass fallback failed for $provinceId: ${e.message}")
                    return RefreshResult(skipped = true, reason = "OSM: ${e.message}")
                }
            }
        } else {
            Log.d(TAG, "OSM cache hit: province=$provinceId age=${nowMs - osmLatest}ms")
        }

        // === 2. Foursquare (optional — only if API key configured) ===
        if (fsqClient != null) {
            val fsqLatest = dao.latestUpdateByProvinceAndSource(provinceId, "foursquare")
            val fsqCacheExpired = fsqLatest == null || (nowMs - fsqLatest) > FSQ_CACHE_TTL_MS

            if (force || fsqCacheExpired) {
                try {
                    Log.i(
                        TAG,
                        "FSQ refresh: province=$provinceId force=$force expired=$fsqCacheExpired",
                    )
                    val allParsed = mutableListOf<Restaurant>()
                    for (query in FSQ_FOOD_QUERIES) {
                        for (offset in 0 until FSQ_MAX_PAGES * FSQ_PAGE_SIZE step FSQ_PAGE_SIZE) {
                            val rawJson = fsqClient.searchPlaces(query, bbox, FSQ_PAGE_SIZE, offset)
                            val batch = fsqImporter.parse(rawJson, provinceId, districtId, nowMs)
                            if (batch.isEmpty()) break
                            for (r in batch) {
                                if (allParsed.none { it.id == r.id }) allParsed += r
                            }
                            if (batch.size < FSQ_PAGE_SIZE) break
                        }
                    }
                    if (allParsed.isNotEmpty()) {
                        dao.deleteByProvinceAndSource(provinceId, "foursquare")
                        dao.insertAll(allParsed)
                        Log.i(TAG, "FSQ saved: ${allParsed.size} records for $provinceId")
                    }
                } catch (e: FoursquareException) {
                    Log.w(TAG, "FSQ fetch failed for $provinceId: ${e.message}")
                }
            }
        }

        return RefreshResult(
            skipped = false,
            osmCount = dao.countByProvinceAndSource(provinceId, "osm"),
        )
    }

    /** Cache status สำหรับ province — ใช้แสดง "อัปเดตเมื่อ..." (M1+) */
    suspend fun cacheStatusByProvince(provinceId: String): CacheStatus {
        val nowMs = System.currentTimeMillis()
        val osmLatest = dao.latestUpdateByProvinceAndSource(provinceId, "osm")
        val fsqLatest = dao.latestUpdateByProvinceAndSource(provinceId, "foursquare")
        return CacheStatus(
            areaId = provinceId,
            osmCount = dao.countByProvinceAndSource(provinceId, "osm"),
            fsqCount = dao.countByProvinceAndSource(provinceId, "foursquare"),
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

        /**
         * FSQ food-related text queries (FSQ v3 ignores `categories` param).
         * ต้องวน query list + dedupe เพราะ v3 ไม่มี category filter
         * Shape from scripts/setup-chiangmai.mjs
         */
        private val FSQ_FOOD_QUERIES = listOf("restaurant", "cafe", "thai", "noodle", "coffee", "street_food")

        /** FSQ page size (max = 50 ต่อ call) */
        private const val FSQ_PAGE_SIZE = 50

        /** FSQ max pages ต่อ query (3 × 50 = 150 ต่อ query) */
        private const val FSQ_MAX_PAGES = 3
    }
}

data class RefreshResult(
    val skipped: Boolean,
    val reason: String? = null,
    val osmCount: Int = 0,
)

data class CacheStatus(
    val areaId: String,                    // province.id (or district.id for drill-down) — เคยชื่อ cityId (M1.b rename)
    val osmCount: Int,
    val fsqCount: Int,
    val osmUpdatedAt: Long?,
    val fsqUpdatedAt: Long?,
    val nowMs: Long,
) {
    val osmAgeMs: Long? get() = osmUpdatedAt?.let { nowMs - it }
    val fsqAgeMs: Long? get() = fsqUpdatedAt?.let { nowMs - it }
}
