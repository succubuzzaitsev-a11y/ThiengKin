package com.thiengkin.data.remote

import android.util.Log
import com.thiengkin.data.Restaurant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SupabaseImporter — parse PostgREST JSON array → List<Restaurant>
 *
 * **PostgREST response format (one restaurant per object):**
 * ```
 * [
 *   {
 *     "id": "osm_268242835",
 *     "name": "Manna",
 *     "name_th": null,
 *     "category": "ร้านอาหาร",
 *     "category_slug": null,
 *     "lat": 13.747702,
 *     "lng": 100.534554,
 *     "tags": ["cuisine:japanese"],
 *     "source": "osm",
 *     "is_favorite": false,
 *     "province_id": "bangkok",
 *     "district_id": null,
 *     "source_updated_at": 1752345678901
 *   },
 *   ...
 * ]
 * ```
 *
 * **Schema source of truth:** `supabase/migrations/001_initial_schema.sql`
 *   ↔ Android `Restaurant` entity (camelCase in Kotlin, snake_case in DB)
 *
 * **Why source="osm":** M3.c pushed OSM-parsed data into Supabase with `source='osm'`.
 * Android-side cache check uses `(province_id, source)` as the key — preserving
 * 'osm' label here means existing cache logic in RestaurantDao still works
 * (no schema migration needed in Room).
 */
class SupabaseImporter {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param rawJson PostgREST response — JSON array of restaurant rows
     * @param provinceId Province.id (e.g. "bangkok") — used as fallback if row.province_id is empty
     * @param districtId District.id (e.g. "phra_nakhon") — used as fallback if row.district_id is null
     * @param nowMs timestamp (millis) — set on source_updated_at for downstream cache logic
     * @return list of Restaurant with source="osm"
     */
    fun parse(
        rawJson: String,
        provinceId: String,
        districtId: String? = null,
        nowMs: Long,
    ): List<Restaurant> {
        return try {
            val root = json.parseToJsonElement(rawJson)
            // PostgREST can return either an array `[{...}]` or an object with `data` field
            // (when Content-Negotiation kicks in). Handle both.
            val array: JsonArray = when {
                root is JsonArray -> root
                root is JsonObject && root["data"] is JsonArray -> root["data"]!!.jsonArray
                else -> {
                    Log.w(TAG, "Unexpected PostgREST root shape: ${root::class.simpleName}")
                    return emptyList()
                }
            }

            array.mapNotNull { el -> parseRow(el, provinceId, districtId, nowMs) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Supabase JSON", e)
            emptyList()
        }
    }

    private fun parseRow(
        el: JsonElement,
        provinceId: String,
        districtId: String?,
        nowMs: Long,
    ): Restaurant? {
        val obj = el.jsonObject
        val id = obj.string("id") ?: return null
        val name = obj.string("name") ?: return null
        if (name.isBlank()) return null

        val lat = obj.double("lat") ?: return null
        val lng = obj.double("lng") ?: return null

        val tags = obj.jsonArray("tags").mapNotNull { it.jsonPrimitive.contentOrNull }

        // Preserve source from DB (always "osm" for M3.c data, but trust the row)
        val source = obj.string("source") ?: "osm"

        // provinceId/districtId: prefer row values (drill-down correctness), fall back to query params
        val rowProvinceId = obj.string("province_id") ?: provinceId
        val rowDistrictId = obj.string("district_id") ?: districtId

        return Restaurant(
            id = id,
            name = name,
            nameTh = obj.string("name_th"),
            category = obj.string("category"),
            categorySlug = obj.string("category_slug"),
            lat = lat,
            lng = lng,
            address = obj.string("address"),
            district = obj.string("district"),
            province = obj.string("province"),
            tel = obj.string("tel"),
            website = obj.string("website"),
            rating = obj.double("rating"),
            reviewCount = obj.int("review_count"),
            price = obj.int("price"),
            tags = tags,
            source = source,
            isFavorite = obj.bool("is_favorite") ?: false,
            photoUrl = obj.string("photo_url"),
            menuText = obj.string("menu_text"),
            aiSummary = obj.string("ai_summary"),
            provinceId = rowProvinceId,
            districtId = rowDistrictId,
            openingHours = obj.string("opening_hours"),
            capacity = obj.int("capacity"),
            // DB-side `source_updated_at` reflects when M3.c pushed the data.
            // Override with nowMs so the cache check in refreshArea() uses refresh-time,
            // not push-time (which can be hours/days old in the DB).
            sourceUpdatedAt = nowMs,
        )
    }

    // === JSON helpers ===

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.jsonArray(key: String): JsonArray =
        (this[key] as? JsonArray) ?: JsonArray(emptyList())

    companion object {
        private const val TAG = "SupabaseImporter"
    }
}
