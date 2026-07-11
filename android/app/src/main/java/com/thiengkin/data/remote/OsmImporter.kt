package com.thiengkin.data.remote

import android.util.Log
import com.thiengkin.data.Restaurant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * OsmImporter — parse Overpass API JSON response → List<Restaurant>
 *
 * Overpass response format:
 * ```
 * {
 *   "version": 0.6,
 *   "elements": [
 *     { "type": "node", "id": 123, "lat": 13.7, "lon": 100.5,
 *       "tags": { "amenity": "restaurant", "name": "...", "cuisine": "thai" } },
 *     { "type": "way", "id": 456, "center": { "lat": 13.7, "lon": 100.5 },
 *       "tags": { ... } }
 *   ]
 * }
 * ```
 *
 * - node → lat/lon from element root
 * - way → lat/lon from element.center (Overpass `out body` on way มี center)
 * - relation → skip (ไม่ค่อยมี restaurant เป็น relation)
 *
 * Field mapping (OSM tags → Restaurant):
 * - name → name
 * - name:th → nameTh (fallback)
 * - name:en → name (fallback ถ้าไม่มี name:th)
 * - cuisine → categorySlug + tags
 * - amenity → category ("ร้านอาหาร", "คาเฟ่", "ฟาสต์ฟู้ด", "ศูนย์อาหาร")
 * - addr:full / addr:street + addr:housenumber → address
 * - addr:city → district
 * - phone / contact:phone → tel
 * - website / contact:website → website
 * - opening_hours → openingHours
 * - capacity → capacity (Int)
 * - image / contact:image → photoUrl
 * - wheelchair / internet_access / payment:* / outdoor_seating / takeaway / delivery / dog / highchair / kids_area / air_conditioning / smoking / diet:* / drive_through / changing_table / bar → tags
 */
class OsmImporter {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param rawJson Overpass API response (JSON)
     * @param cityId city id (e.g. "bkk") — tagged on every record
     * @param nowMs timestamp (millis) — set on source_updated_at
     * @return list of Restaurant with source="osm"
     */
    fun parse(rawJson: String, cityId: String, nowMs: Long): List<Restaurant> {
        return try {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val elements = root["elements"]?.jsonArray ?: return emptyList()

            elements.mapNotNull { el -> parseElement(el, cityId, nowMs) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Overpass JSON", e)
            emptyList()
        }
    }

    private fun parseElement(el: JsonElement, cityId: String, nowMs: Long): Restaurant? {
        val obj = el.jsonObject
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toString() ?: return null
        val tags = obj["tags"]?.jsonObject

        // Skip records without name
        val name = tags?.getName() ?: return null
        if (name.isBlank()) return null

        // lat/lng — different paths for node vs way
        val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull
            ?: obj["center"]?.jsonObject?.get("lat")?.jsonPrimitive?.doubleOrNull
            ?: return null
        val lng = obj["lon"]?.jsonPrimitive?.doubleOrNull
            ?: obj["center"]?.jsonObject?.get("lon")?.jsonPrimitive?.doubleOrNull
            ?: return null

        val amenity = tags.getString("amenity")
        val cuisine = tags.getString("cuisine")
        val category = mapAmenityToCategory(amenity)
        val categorySlug = cuisine?.split(";")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        // Tags: cuisine + OSM amenity tags
        val allTags = buildList {
            if (!cuisine.isNullOrBlank()) add("cuisine:$cuisine")
            // Amenity-derived tags — ใช้สำหรับ filter chip
            for (k in AMENITY_TAG_KEYS) {
                tags.getString(k)?.let { v -> add("$k:$v") }
            }
            // Boolean tags
            for (k in BOOLEAN_TAG_KEYS) {
                tags.getString(k)?.let { v -> if (v == "yes") add(k) }
            }
        }

        return Restaurant(
            id = "osm_$id",
            name = name,
            nameTh = tags.getString("name:th"),
            category = category,
            categorySlug = categorySlug,
            lat = lat,
            lng = lng,
            address = buildAddress(tags),
            district = tags.getString("addr:city") ?: tags.getString("addr:suburb"),
            province = tags.getString("addr:province") ?: tags.getString("addr:state"),
            tel = tags.getString("contact:phone") ?: tags.getString("phone"),
            website = tags.getString("contact:website") ?: tags.getString("website"),
            rating = null,                              // OSM ไม่มี rating
            reviewCount = null,
            price = null,
            tags = allTags,
            source = "osm",
            isFavorite = false,
            photoUrl = tags.getString("contact:image") ?: tags.getString("image"),
            menuText = null,
            aiSummary = null,
            cityId = cityId,
            openingHours = tags.getString("opening_hours"),
            capacity = tags.getString("capacity")?.toIntOrNull(),
            sourceUpdatedAt = nowMs,
        )
    }

    private fun buildAddress(tags: JsonObject?): String? {
        if (tags == null) return null
        val full = tags.getString("addr:full")
        if (!full.isNullOrBlank()) return full

        val parts = buildList {
            tags.getString("addr:housenumber")?.let { add(it) }
            tags.getString("addr:street")?.let { add(it) }
            tags.getString("addr:suburb")?.let { add("ต.${it}") }
            tags.getString("addr:city")?.let { add("อ.${it}") }
        }
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    private fun mapAmenityToCategory(amenity: String?): String? = when (amenity) {
        "restaurant" -> "ร้านอาหาร"
        "cafe" -> "คาเฟ่"
        "fast_food" -> "ฟาสต์ฟู้ด"
        "food_court" -> "ศูนย์อาหาร"
        else -> amenity
    }

    /** name → name:th → name:en (priority) */
    private fun JsonObject.getName(): String? =
        getString("name:th") ?: getString("name") ?: getString("name:en")

    private fun JsonObject.getString(key: String): String? {
        val v = this[key] ?: return null
        if (v is JsonPrimitive && v.isString.not() && v.contentOrNull == null) return null
        return v.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "OsmImporter"

        /** Amenity keys ที่เก็บเป็น tags (มี value หลายแบบ) */
        private val AMENITY_TAG_KEYS = listOf(
            "cuisine",
            "wheelchair",
            "internet_access",
            "outdoor_seating",
            "air_conditioning",
            "smoking",
            "dog",
            "drive_through",
            "changing_table",
            "highchair",
            "kids_area",
            "bar",
            "payment:credit_card",
            "payment:cash",
            "payment:debit_card",
            "takeaway",
            "delivery",
            "diet:vegetarian",
            "diet:vegan",
            "diet:halal",
        )

        /** Boolean yes/no tags — เก็บเฉพาะ=yes (มี feature นั้น) */
        private val BOOLEAN_TAG_KEYS = listOf(
            "wheelchair",
            "internet_access",
            "outdoor_seating",
            "air_conditioning",
            "takeaway",
            "delivery",
            "dog",
            "highchair",
            "kids_area",
            "drive_through",
            "changing_table",
            "bar",
            "payment:credit_card",
            "diet:vegetarian",
            "diet:vegan",
            "diet:halal",
        )

        private val JsonPrimitive.doubleOrNull: Double?
            get() = contentOrNull?.toDoubleOrNull()
    }
}
