package com.thiengkin.data.remote

import android.util.Log
import com.thiengkin.data.BoundingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OsmClient — OpenStreetMap Overpass API client
 *
 * Endpoint: https://overpass-api.de/api/interpreter
 * Method: POST with `data=<query>` body (URL-encoded)
 *
 * Overpass QL query:
 *   [out:json][timeout:25];
 *   (node["amenity"~"restaurant|cafe|fast_food|food_court"](bbox););
 *   out body;>;out skel qt;
 *
 * Rate limit (verified wiki 2026-07-11): 10,000 req/day, 1 GB download — เราใช้แค่
 * 1 call/เมือง/refresh → 10 เมือง = 10 calls ต่อ refresh cycle
 *
 * User-Agent: REQUIRED ตาม OSM policy — ระบุ app name + version
 */
class OsmClient(
    private val httpClient: OkHttpClient = defaultClient,
) {
    /**
     * Fetch all restaurants/cafes/fast_food/food_court nodes & ways within a bbox.
     *
     * @param bbox city bounding box
     * @return raw JSON string (Overpass response) — caller parses via [OsmImporter]
     * @throws OsmException on network/HTTP/parse failure
     */
    suspend fun fetchRestaurants(bbox: BoundingBox): String = withContext(Dispatchers.IO) {
        val query = buildQuery(bbox)
        val url = BASE_URL.toHttpUrl()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .post(query.toRequestBody(FORM_MEDIA_TYPE))
            .build()

        Log.d(TAG, "POST $BASE_URL (${bbox.toOverpassString()})")
        execute(request)
    }

    private fun buildQuery(bbox: BoundingBox): String = """
        [out:json][timeout:25];
        (
          node["amenity"~"restaurant|cafe|fast_food|food_court"](${bbox.toOverpassString()});
          way["amenity"~"restaurant|cafe|fast_food|food_court"](${bbox.toOverpassString()});
        );
        out body;
        >;
        out skel qt;
    """.trimIndent()

    private suspend fun execute(request: Request): String =
        suspendCancellableCoroutine { cont ->
            val call = httpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "Overpass request failed: ${e.message}")
                    cont.resumeWithException(OsmException.NetworkError(e.message ?: "unknown"))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        if (!r.isSuccessful) {
                            Log.w(TAG, "Overpass HTTP ${r.code}: ${r.message}")
                            cont.resumeWithException(
                                OsmException.HttpError(r.code, r.message)
                            )
                            return
                        }
                        val body = r.body?.string()
                        if (body.isNullOrBlank()) {
                            cont.resumeWithException(OsmException.EmptyResponse)
                            return
                        }
                        cont.resume(body)
                    }
                }
            })
        }

    companion object {
        private const val TAG = "OsmClient"
        private const val BASE_URL = "https://overpass-api.de/api/interpreter"
        private const val USER_AGENT = "ThiengKin/0.1.0 (Android; +https://thiengkin.app)"

        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()

        /** Default client — 25s timeout (Overpass timeout=25) + 10s connect/read */
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

/** Overpass API error types — ใช้ sealed class เพื่อให้ caller จัดการได้ละเอียด */
sealed class OsmException(message: String) : Exception(message) {
    data class NetworkError(val detail: String) : OsmException("Network error: $detail")
    data class HttpError(val code: Int, val msg: String) : OsmException("HTTP $code: $msg")
    data object EmptyResponse : OsmException("Empty response from Overpass")
}
