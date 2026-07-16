package com.thiengkin.data.remote

import android.util.Log
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
 * SupabaseClient — PostgREST client for `restaurants` table
 *
 * M3.d: wire M3.c push pipeline into Android — read OSM data from Supabase mirror
 * instead of fetching directly from OSM Overpass (which has aggressive rate limits).
 *
 * **Endpoint shape (PostgREST):**
 *   GET {supabaseUrl}/rest/v1/restaurants?select=*&province_id=eq.{id}&source=in.(osm,serpapi)
 *   [&district_id=eq.{districtId}]
 *   [&limit=N]
 *
 * **Auth (read-only public):**
 *   - Header `apikey: <anon_key>`           (Supabase-specific, required even for public reads)
 *   - Header `Authorization: Bearer <anon>` (PostgREST requires this too)
 *   - ใช้ anon/Publishable key เท่านั้น — RLS-enforced, safe to ship in client
 *   - ห้ามใช้ Secret/service_role key — bypasses RLS = security hole
 *
 * **Why PostgREST not Supabase SDK:**
 *   - Project already uses OkHttp + kotlinx.serialization (mirror OsmClient/FoursquareClient)
 *   - SDK adds ~2MB + Ktor stack — overkill for 1 table read
 *   - PostgREST is a plain HTTP API, easy to debug with curl
 *
 * **M3.d design notes:**
 *   - All data in `restaurants` table came from M3.c (osm-parse + push-osm pipeline)
 *   - Therefore source='osm' rows are the canonical Android-side "OSM" data
 *   - No district filter for province-level reads; only filter when drill-down
 *   - `source_updated_at` from DB is preserved (use null since we re-set on import to Room)
 */
class SupabaseClient(
    private val baseUrl: String,
    private val anonKey: String,
    private val httpClient: OkHttpClient = defaultClient,
) {
    init {
        require(baseUrl.isNotBlank()) { "SupabaseClient: baseUrl must not be blank" }
        require(anonKey.isNotBlank()) { "SupabaseClient: anonKey must not be blank" }
    }

    /**
     * Fetch all OSM/SerpApi-source restaurants for a province (and optionally district).
     *
     * PostgREST query:
     *   restaurants?select=*&province_id=eq.{provinceId}&source=in.(osm,serpapi)[&district_id=eq.X]
     *
     * Includes both:
     *   - source='osm' (33,442 OSM Overpass rows nationwide from M3.c)
     *   - source='serpapi' (Google Maps data via SerpApi, M6 enrichment)
     *
     * @param provinceId Province.id (e.g. "bangkok", "chiang_mai")
     * @param districtId District.id (e.g. "phra_nakhon") — null = whole province
     * @return raw JSON array string (PostgREST format) — caller parses via [SupabaseImporter]
     * @throws SupabaseException on network/HTTP/auth failure
     */
    suspend fun fetchRestaurantsByProvince(
        provinceId: String,
        districtId: String? = null,
    ): String = withContext(Dispatchers.IO) {
        require(provinceId.isNotBlank()) { "provinceId must not be blank" }

        val url = buildBaseUrl().newBuilder()
            .addQueryParameter("select", SELECT_COLUMNS)
            .addQueryParameter("province_id", "eq.$provinceId")
            .addQueryParameter("source", "in.(osm,serpapi)")  // M6: include SerpApi Google Maps data alongside OSM
            .apply {
                if (districtId != null) {
                    addQueryParameter("district_id", "eq.$districtId")
                }
            }
            // PostgREST default limit = 1000 rows; Thailand nationwide ~5k → ask for more
            // Cap at 10k to be safe (a single province won't exceed this in practice).
            .addQueryParameter("limit", "10000")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Accept", "application/json")
            .build()

        Log.d(TAG, "GET $url")
        execute(request)
    }

    private fun buildBaseUrl() =
        "$baseUrl/rest/v1/restaurants".toHttpUrl()

    private suspend fun execute(request: Request): String =
        suspendCancellableCoroutine { cont ->
            val call = httpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w(TAG, "Supabase request failed: ${e.message}")
                    cont.resumeWithException(SupabaseException.NetworkError(e.message ?: "unknown"))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        if (!r.isSuccessful) {
                            val body = r.body?.string()?.take(500) ?: ""
                            Log.w(TAG, "Supabase HTTP ${r.code}: ${r.message} body=$body")
                            cont.resumeWithException(
                                SupabaseException.HttpError(r.code, r.message, body)
                            )
                            return
                        }
                        val body = r.body?.string()
                        if (body.isNullOrBlank()) {
                            cont.resumeWithException(SupabaseException.EmptyResponse)
                            return
                        }
                        cont.resume(body)
                    }
                }
            })
        }

    companion object {
        private const val TAG = "SupabaseClient"

        /**
         * Columns to select. ต้องตรงกับ schema ใน 001_initial_schema.sql + field names
         * ใน Restaurant.kt (snake_case ↔ camelCase).
         *
         * ***ถ้าเพิ่ม field ใน 001 schema:*** เพิ่ม column ในนี้ + map ใน SupabaseImporter
         */
        private const val SELECT_COLUMNS = "id,name,name_th,category,category_slug,lat,lng," +
            "address,district,province,tel,website," +
            "rating,review_count,price,tags,source,is_favorite," +
            "photo_url,menu_text,ai_summary," +
            "province_id,district_id,opening_hours,capacity,source_updated_at"

        /** Default client — 10s connect, 30s read (Supabase PostgREST is fast) */
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

/** Supabase / PostgREST error types */
sealed class SupabaseException(message: String) : Exception(message) {
    data class NetworkError(val detail: String) : SupabaseException("Network error: $detail")
    data class HttpError(val code: Int, val msg: String, val body: String) :
        SupabaseException("HTTP $code: $msg")
    data object EmptyResponse : SupabaseException("Empty response from Supabase")
    data object NotConfigured :
        SupabaseException("Supabase URL or anon key not configured in BuildConfig")
}
