package com.thiengkin.data.remote

import android.util.Log
import com.thiengkin.data.BoundingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * FoursquareClient — Foursquare Places API v3 client (places-api.foursquare.com)
 *
 * Endpoint: https://places-api.foursquare.com/places/search
 * Auth: Header `Authorization: Bearer <API_KEY>` (FSQ_API_KEY in BuildConfig)
 * Version: `X-Places-Api-Version: 2025-06-17`
 * Free tier (verified 2026-07-11): **500 calls/month** (ลดจาก 10,000 เมืื่อ June 1, 2026)
 *
 * **สำคัญ (v3 breaking changes จาก v2):**
 * - Base URL เปลี่ยน: `places-api.foursquare.com` (ไม่ใช่ `api.foursquare.com/v3`)
 * - Auth header: `Bearer <key>` (ไม่ใช่ `<key>` ตรงๆ)
 * - `categories` param ถูก IGNORED — ใช้ `query=<text>` + `sort=RELEVANCE` แทน
 *
 * Default fields (Pro): fsq_id, name, geocode, location, categories, chains, link
 * Premium (NOT free): rating, popularity, price, hours, tel, website, email, social_media, photos, tips
 *
 * ใช้สำหรับ: เพิ่ม POI coverage (FSQ มี POI database ต่างจาก OSM)
 * Rate strategy: cache 30 วัน ใน Room (1 call/เมือง/เดือน)
 */
class FoursquareClient(
    private val apiKey: String,
    private val httpClient: OkHttpClient = OsmClient.defaultClient,
) {
    /**
     * Search places by text query within a bbox (FSQ v3 pattern).
     *
     * @param query food-related search term (e.g. "restaurant", "cafe", "thai")
     *              FSQ v3 IGNORES the `categories` param — caller must pass text queries.
     * @param bbox city bounding box
     * @param limit max results per call (FSQ allows up to 50)
     * @param offset pagination offset (default 0)
     * @return raw JSON string — caller parses via [FoursquareImporter]
     */
    suspend fun searchPlaces(
        query: String,
        bbox: BoundingBox,
        limit: Int = 50,
        offset: Int = 0,
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw FoursquareException.MissingApiKey
        }
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("ll", "${bbox.centerLat},${bbox.centerLng}")
            .addQueryParameter("radius", bbox.radiusMeters.toString())
            .addQueryParameter("query", query)
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("sort", "RELEVANCE")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("X-Places-Api-Version", API_VERSION)
            .header("Accept", "application/json")
            .build()

        Log.d(TAG, "GET $url")
        execute(request)
    }

    private suspend fun execute(request: Request): String =
        suspendCancellableCoroutine { cont ->
            val call = httpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "FSQ request failed: ${e.message}")
                    cont.resumeWithException(FoursquareException.NetworkError(e.message ?: "unknown"))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        if (!r.isSuccessful) {
                            val body = r.body?.string()
                            Log.w(TAG, "FSQ HTTP ${r.code}: ${r.message} body=${body?.take(200)}")
                            cont.resumeWithException(
                                FoursquareException.HttpError(r.code, r.message, body ?: "")
                            )
                            return
                        }
                        val body = r.body?.string()
                        if (body.isNullOrBlank()) {
                            cont.resumeWithException(FoursquareException.EmptyResponse)
                            return
                        }
                        cont.resume(body)
                    }
                }
            })
        }

    companion object {
        private const val TAG = "FoursquareClient"
        private const val BASE_URL = "https://places-api.foursquare.com/places/search"
        private const val API_VERSION = "2025-06-17"
    }
}

/** BoundingBox extensions for FSQ API (which uses center+radius, not bbox) */
private val BoundingBox.centerLat: Double get() = (south + north) / 2.0
private val BoundingBox.centerLng: Double get() = (west + east) / 2.0
private val BoundingBox.radiusMeters: Int
    get() {
        val midLat = centerLat
        val heightKm = (north - south) * 111.0 / 2.0
        val widthKm = (east - west) * 111.0 * kotlin.math.cos(Math.toRadians(midLat)) / 2.0
        // ใช้ระยะที่ยาวกว่า +10% margin (กัน FSQ clip)
        val maxRadius = maxOf(heightKm, widthKm) * 1000.0 * 1.1
        return maxRadius.toInt().coerceAtMost(100_000)  // FSQ max = 100km
    }

sealed class FoursquareException(message: String) : Exception(message) {
    data object MissingApiKey : FoursquareException("FSQ_API_KEY not configured")
    data class NetworkError(val detail: String) : FoursquareException("Network error: $detail")
    data class HttpError(val code: Int, val msg: String, val body: String) :
        FoursquareException("HTTP $code: $msg")
    data object EmptyResponse : FoursquareException("Empty response from Foursquare")
}
