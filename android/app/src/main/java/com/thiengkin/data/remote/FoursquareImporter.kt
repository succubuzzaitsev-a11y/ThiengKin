package com.thiengkin.data.remote

import android.util.Log
import com.thiengkin.data.Restaurant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * FoursquareImporter — parse FSQ Places API search response → List<Restaurant>
 *
 * FSQ Places API v3 search response (default fields):
 * ```
 * {
 *   "results": [
 *     {
 *       "fsq_id": "abc123",
 *       "name": "ก๋วยเตี๋ยวลูกชาย",
 *       "geocodes": { "main": { "latitude": 13.7, "longitude": 100.5 } },
 *       "location": {
 *         "address": "69 Charoen Krung",
 *         "locality": "Bang Rak",
 *         "region": "Bangkok",
 *         "country": "TH",
 *         "formatted_address": "..."
 *       },
 *       "categories": [{ "id": 13065, "name": "Restaurant" }],
 *       "link": "/v3/places/abc123"
 *     }
 *   ]
 * }
 * ```
 *
 * Free tier ไม่มี: rating, popularity, price, hours, tel, website, email, photos
 * (ต้องใช้ Premium endpoints = เสียเงิน)
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
        val fsqId = obj["fsq_id"]?.jsonPrimitive?.contentOrNull ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        if (name.isBlank()) return null

        // geocodes.main.latitude / longitude
        val main = obj["geocodes"]?.jsonObject?.get("main")?.jsonObject
        val lat = main?.get("latitude")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return null
        val lng = main?.get("longitude")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return null

        val location = obj["location"]?.jsonObject
        val address = location?.get("formatted_address")?.jsonPrimitive?.contentOrNull
            ?: buildAddress(location)
        val district = location?.get("locality")?.jsonPrimitive?.contentOrNull
            ?: location?.get("region")?.jsonPrimitive?.contentOrNull
        val province = location?.get("region")?.jsonPrimitive?.contentOrNull

        val categories = obj["categories"]?.jsonArray
        val firstCategory = categories?.firstOrNull()?.jsonObject
        val categoryName = firstCategory?.get("name")?.jsonPrimitive?.contentOrNull

        return Restaurant(
            id = "fsq_$fsqId",
            name = name,
            nameTh = null,  // FSQ ไม่มี multi-language ใน default fields
            category = categoryName,
            categorySlug = "fsq:${firstCategory?.get("id")?.jsonPrimitive?.contentOrNull}",
            lat = lat,
            lng = lng,
            address = address,
            district = district,
            province = province,
            tel = null,       // Premium only
            website = null,   // Premium only
            rating = null,    // Premium only
            reviewCount = null,
            price = null,     // Premium only
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
