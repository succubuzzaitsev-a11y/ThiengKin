package com.thiengkin.data.remote

import android.util.Log
import com.thiengkin.data.Restaurant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * FoursquareImporter — parse FSQ Places API search response → List<Restaurant>
 *
 * รองรับทั้ง 2 format:
 *
 * 1. FSQ Places API v3 (NEW — places-api.foursquare.com, version 2025-06-17):
 * ```
 * {
 *   "results": [
 *     {
 *       "fsq_place_id": "abc123",          // เปลี่ยนจาก fsq_id
 *       "name": "ก๋วยเตี๋ยวลูกชาย",
 *       "latitude": 13.7,                   // top-level (ไม่ใช่ geocodes.main แล้ว)
 *       "longitude": 100.5,
 *       "location": {
 *         "address": "69 Charoen Krung",
 *         "locality": "Bang Rak",
 *         "region": "Bangkok",
 *         "country": "TH",
 *         "formatted_address": "..."
 *       },
 *       "categories": [{ "id": 13065, "name": "Restaurant" }],
 *       "link": "/v3/places/abc123",        // field ตรงๆ (ไม่ต้อง construct)
 *       "tel": "...",
 *       "website": "...",
 *       "price": 2
 *     }
 *   ]
 * }
 * ```
 *
 * 2. FSQ Places API v2 (legacy — api.foursquare.com/v3/places/search):
 * ```
 * {
 *   "results": [
 *     {
 *       "fsq_id": "abc123",
 *       "geocodes": { "main": { "latitude": 13.7, "longitude": 100.5 } },
 *       ...
 *     }
 *   ]
 * }
 * ```
 *
 * Free tier (verified 2026-07-11): **500 calls/month** (ลดจาก 10,000)
 * Free tier ไม่มี: rating, popularity, photos, tips, hours (บาง field)
 * (Premium = เสียเงิน)
 */
class FoursquareImporter {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawJson: String, cityId: String, nowMs: Long): List<Restaurant> {
        return try {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val results = root["results"]?.jsonArray ?: return emptyList()

            results.mapNotNull { el -> parseResult(el, cityId, nowMs) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse FSQ JSON", e)
            emptyList()
        }
    }

    private fun parseResult(el: kotlinx.serialization.json.JsonElement, cityId: String, nowMs: Long): Restaurant? {
        val obj = el.jsonObject

        // FSQ v3: fsq_place_id | FSQ v2: fsq_id (legacy)
        val fsqId = obj["fsq_place_id"]?.jsonPrimitive?.contentOrNull
            ?: obj["fsq_id"]?.jsonPrimitive?.contentOrNull
            ?: return null

        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        if (name.isBlank()) return null

        // FSQ v3: latitude/longitude ที่ top-level
        // FSQ v2: geocodes.main.latitude/longitude (legacy)
        val lat = obj["latitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            ?: obj["geocodes"]?.jsonObject?.get("main")?.jsonObject
                ?.get("latitude")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            ?: return null
        val lng = obj["longitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            ?: obj["geocodes"]?.jsonObject?.get("main")?.jsonObject
                ?.get("longitude")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            ?: return null

        val location = obj["location"]?.jsonObject
        val address = location?.get("formatted_address")?.jsonPrimitive?.contentOrNull
            ?: buildAddress(location)
        val district = location?.get("locality")?.jsonPrimitive?.contentOrNull
            ?: location?.get("region")?.jsonPrimitive?.contentOrNull
        val province = location?.get("region")?.jsonPrimitive?.contentOrNull

        val categories = obj["categories"]?.jsonArray
        val firstCategory = categories?.firstOrNull()?.jsonObject
        val categoryName = firstCategory?.get("name")?.jsonPrimitive?.contentOrNull
        val categoryId = firstCategory?.get("id")?.jsonPrimitive?.contentOrNull

        // FSQ v3: tel/website/price เป็น default fields (ฟรี)
        // FSQ v2: ไม่มี (premium only)
        val tel = obj["tel"]?.jsonPrimitive?.contentOrNull
        val website = obj["website"]?.jsonPrimitive?.contentOrNull
        val price = obj["price"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

        return Restaurant(
            id = "fsq_$fsqId",
            name = name,
            nameTh = null,  // FSQ ไม่มี multi-language ใน default fields
            category = categoryName,
            categorySlug = if (categoryId != null) "fsq:$categoryId" else null,
            lat = lat,
            lng = lng,
            address = address,
            district = district,
            province = province,
            tel = tel,            // FSQ v3 free field (fallback null = v2)
            website = website,    // FSQ v3 free field (fallback null = v2)
            rating = null,        // Premium only
            reviewCount = null,
            price = price,        // FSQ v3 free field (fallback null = v2)
            tags = emptyList(),
            source = "foursquare",
            isFavorite = false,
            photoUrl = null,  // Premium only
            menuText = null,
            aiSummary = null,
            cityId = cityId,
            openingHours = null,  // Premium only
            capacity = null,
            sourceUpdatedAt = nowMs,
        )
    }

    private fun buildAddress(location: JsonObject?): String? {
        if (location == null) return null
        val parts = buildList {
            location.get("address")?.jsonPrimitive?.contentOrNull?.let { add(it) }
            location.get("locality")?.jsonPrimitive?.contentOrNull?.let { add(it) }
            location.get("region")?.jsonPrimitive?.contentOrNull?.let { add(it) }
        }
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }

    companion object {
        private const val TAG = "FoursquareImporter"
    }
}
