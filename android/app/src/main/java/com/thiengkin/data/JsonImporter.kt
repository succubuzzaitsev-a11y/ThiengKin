package com.thiengkin.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Import seed-restaurants.json (assets/) → Room
 *
 * ทำครั้งเดียวตอน first launch (เช็คจาก row count)
 * ถ้า import สำเร็จ → rowCount ตามไฟล์
 * ถ้า fail → log error + return false (app ยังเปิดได้ แต่ list ว่าง)
 */
class JsonImporter(
    private val context: Context,
    private val dao: RestaurantDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true  // schema อาจมี field เพิ่ม (photos, menu, etc.)
        coerceInputValues = true
    }

    suspend fun importIfEmpty(): ImportResult = withContext(Dispatchers.IO) {
        val existing = dao.count()
        if (existing > 0) {
            Log.i(TAG, "DB already has $existing restaurants — skip import")
            return@withContext ImportResult(skipped = true, count = existing)
        }

        try {
            val raw = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            val wrapper = json.decodeFromString<RestaurantFile>(raw)
            val list = wrapper.restaurants.map { it.toEntity() }

            dao.insertAll(list)
            Log.i(TAG, "Imported ${list.size} restaurants from $ASSET_FILE")
            ImportResult(skipped = false, count = list.size)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            ImportResult(skipped = false, count = 0, error = e.message)
        }
    }

    private fun RestaurantDto.toEntity() = Restaurant(
        id = id,
        name = name,
        nameTh = name_th,
        category = category,
        categorySlug = category_slug,
        lat = lat,
        lng = lng,
        address = address,
        district = district,
        province = province,
        tel = tel,
        website = website,
        rating = rating,
        reviewCount = review_count,
        price = price,
        tags = tags,
        source = source,
        isFavorite = false,
        photoUrl = null,
        menuText = null,
        aiSummary = null,
        // Phase 2 defaults (manual seed ไม่มี city)
        cityId = "",
        openingHours = null,
        capacity = null,
        sourceUpdatedAt = null,
    )

    @Serializable
    data class RestaurantFile(
        val metadata: Map<String, String> = emptyMap(),
        val restaurants: List<RestaurantDto>,
    )

    @Serializable
    data class RestaurantDto(
        val id: String,
        val name: String,
        val name_th: String? = null,
        val category: String? = null,
        val category_slug: String? = null,
        val lat: Double,
        val lng: Double,
        val address: String? = null,
        val district: String? = null,
        val province: String? = null,
        val tel: String? = null,
        val website: String? = null,
        val rating: Double? = null,
        val review_count: Int? = null,
        val price: Int? = null,
        val tags: List<String> = emptyList(),
        val source: String = "foursquare",
    )

    data class ImportResult(
        val skipped: Boolean,
        val count: Int,
        val error: String? = null,
    )

    companion object {
        private const val TAG = "JsonImporter"
        const val ASSET_FILE = "seed-restaurants.json"
    }
}
