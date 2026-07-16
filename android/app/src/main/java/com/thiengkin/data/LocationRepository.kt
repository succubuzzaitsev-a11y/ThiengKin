package com.thiengkin.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors

/**
 * LocationRepository — current location (foreground only, single fix)
 *
 * Phase 1: ใช้ platform [LocationManager] + [Geocoder] (ไม่พึ่ง GMS)
 * Phase 1.5: เปลี่ยนเป็น FusedLocationProviderClient ถ้าต้องการ accuracy ดีกว่า
 *
 * **Graceful fallback** — ถ้า GPS ไม่พร้อม (permission denied / provider off / ไม่มี fix ใน 5s)
 * → ใช้ตำแหน่งเริ่มต้น (จาก Province selector) เพื่อให้แอปรันได้
 *
 * Flow:
 * 1. requestLocation() → check permission → check provider
 * 2. ถ้าไม่ผ่าน → applyFallback(reason) ใช้ [selectedProvince.centroid] หรือ [DEFAULT_LAT]/[DEFAULT_LNG]
 * 3. ถ้าผ่าน → getLastKnownLocation (instant) → ถ้าไม่มี → requestSingleFix (5s timeout)
 * 4. เมื่อได้ fix → async reverse geocode → emit Granted(lat, lng, address, isFallback=false)
 *
 * M1.b: selectedProvince + selectedDistrict แทน selectedCity (nationwide scope)
 */
class LocationRepository(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val executor = Executors.newSingleThreadExecutor()

    private var timeoutJob: Job? = null
    private var singleUpdateListener: LocationListener? = null

    private val _state = MutableStateFlow<LocationState>(LocationState.Idle)
    val state: StateFlow<LocationState> = _state.asStateFlow()

    /**
     * Selected province — ใช้เป็น fallback location เมื่อ GPS ไม่พร้อม
     * Selected district (optional) — drill-down ในจังหวัด
     *
     * Phase 1.5: เก็บใน memory เท่านั้น (reset เมื่อ kill app) — Phase 2 จะ persist ใน DataStore
     * ตั้งค่าผ่าน [setSelectedProvince] เมื่อ user เลือกจาก ProvincePicker
     *
     * M1.b: เปลี่ยนจาก `selectedCity: City?` (Phase 1.5 — 10 เมือง) เป็น `selectedProvince + selectedDistrict`
     */
    @Volatile
    private var selectedProvince: Province? = null

    @Volatile
    private var selectedDistrict: District? = null

    fun requestLocation() {
        // Cancel any pending timeout from previous request
        timeoutJob?.cancel()
        singleUpdateListener?.let { locationManager.removeUpdates(it) }
        singleUpdateListener = null

        if (!hasPermission()) {
            applyFallback(reason = "ไม่ได้รับอนุญาต GPS — ใช้ตำแหน่งจากเมืองที่เลือก")
            return
        }
        val gpsOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val netOn = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!gpsOn && !netOn) {
            applyFallback(reason = "GPS/Network ปิดอยู่ — ใช้ตำแหน่งจากเมืองที่เลือก")
            return
        }

        // M7: กัน flicker — ถ้ามี Granted อยู่แล้ว ไม่ต้องเปลี่ยนเป็น Loading
        // (user tap "GPS" เพื่อ refresh → ไม่อยากเห็น content หายไป)
        if (_state.value !is LocationState.Granted) {
            _state.value = LocationState.Loading
        }

        // 1) Try last-known fix (instant) — pick freshest across providers
        val providers = listOfNotNull(
            if (gpsOn) LocationManager.GPS_PROVIDER else null,
            if (netOn) LocationManager.NETWORK_PROVIDER else null,
        )
        val lastKnown = pickLastKnown(providers)
        if (lastKnown != null) {
            applyLocation(lastKnown)
            return
        }

        // 2) Fallback: request a single fresh fix with 5s timeout
        scheduleFallbackTimeout(5_000L)
        requestSingleFix(if (gpsOn) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Update selected province (+ optional district) — ใช้เป็น fallback location เมื่อ GPS ไม่พร้อม
     *
     * ถ้าปัจจุบันอยู่ใน fallback state → apply ใหม่ทันที
     * ถ้ามี GPS fix จริงอยู่แล้ว → keep real GPS, save province ไว้ใช้ตอน fallback ครั้งหน้า
     */
    fun setSelectedProvince(province: Province, district: District? = null) {
        selectedProvince = province
        selectedDistrict = district
        val current = _state.value
        if (current is LocationState.Granted && current.isFallback) {
            // Already in fallback — re-apply with new province
            applyFallback(reason = current.fallbackReason ?: "เปลี่ยนจังหวัด")
        }
    }

    /** Current selected province (nullable). ใช้สำหรับ UI display. */
    fun getSelectedProvince(): Province? = selectedProvince

    /** Current selected district (nullable). Drill-down ภายในจังหวัด. */
    fun getSelectedDistrict(): District? = selectedDistrict

    /** Manually mark denied (e.g. user said "no") — call from Activity after permission prompt. */
    fun markDenied() {
        applyFallback(reason = "ไม่ได้รับอนุญาต GPS — ใช้ตำแหน่งเริ่มต้น")
    }

    @SuppressLint("MissingPermission")
    private fun pickLastKnown(providers: List<String>): Location? {
        var best: Location? = null
        for (p in providers) {
            try {
                val l = locationManager.getLastKnownLocation(p) ?: continue
                if (best == null || l.time > best.time) best = l
            } catch (e: SecurityException) {
                Log.w(TAG, "getLastKnownLocation($p) denied", e)
            }
        }
        return best
    }

    @SuppressLint("MissingPermission")
    private fun requestSingleFix(provider: String) {
        if (Build.VERSION.SDK_INT >= 30) {
            // Modern API (API 30+) — non-blocking
            val cancellationSignal = CancellationSignal()
            locationManager.getCurrentLocation(
                provider,
                cancellationSignal,
                executor,
            ) { location ->
                if (location != null) {
                    Log.d(TAG, "getCurrentLocation: ${location.latitude},${location.longitude}")
                    applyLocation(location)
                } else {
                    Log.d(TAG, "getCurrentLocation returned null")
                    // Wait for timeout to fire
                }
            }
        } else {
            // Legacy API (API 24-29)
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    Log.d(TAG, "onLocationChanged: ${location.latitude},${location.longitude}")
                    applyLocation(location)
                    locationManager.removeUpdates(this)
                    singleUpdateListener = null
                }

                @Deprecated("required override on API <30")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

                override fun onProviderEnabled(provider: String) {}

                override fun onProviderDisabled(provider: String) {
                    Log.w(TAG, "Provider disabled: $provider")
                    locationManager.removeUpdates(this)
                    singleUpdateListener = null
                    // Wait for timeout to fire
                }
            }
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    0L,        // minTimeMs
                    0f,        // minDistanceM
                    listener,
                    Looper.getMainLooper(),
                )
                singleUpdateListener = listener
            } catch (e: SecurityException) {
                Log.w(TAG, "requestLocationUpdates denied", e)
                applyFallback(reason = "ขอ Location ไม่สำเร็จ — ใช้ตำแหน่งเริ่มต้น")
            }
        }
    }

    private fun scheduleFallbackTimeout(delayMs: Long) {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(delayMs)
            if (_state.value is LocationState.Loading) {
                Log.w(TAG, "Timeout — falling back to default location")
                applyFallback(reason = "หาสัญญาณ GPS ไม่เจอใน ${delayMs / 1000}s — ใช้ตำแหน่งเริ่มต้น")
            }
        }
    }

    private fun applyLocation(location: Location) {
        // Cancel timeout — we got a real fix
        timeoutJob?.cancel()
        singleUpdateListener?.let { locationManager.removeUpdates(it) }
        singleUpdateListener = null

        val lat = location.latitude
        val lng = location.longitude
        _state.value = LocationState.Granted(
            lat = lat,
            lng = lng,
            address = null,
            isFallback = false,
            fallbackReason = null,
            fixId = System.currentTimeMillis(),  // M6: unique per fix → StateFlow emits
        )
        // Push into RestaurantExt (for distanceMeters / etaMinutes extension properties)
        userLat = lat
        userLng = lng

        // Async reverse geocode
        scope.launch {
            val address = reverseGeocode(lat, lng)
            val current = _state.value
            if (current is LocationState.Granted && !current.isFallback) {
                _state.value = current.copy(address = address)
            }
        }
    }

    private fun applyFallback(reason: String) {
        // Cancel pending requests
        timeoutJob?.cancel()
        singleUpdateListener?.let { locationManager.removeUpdates(it) }
        singleUpdateListener = null

        // ใช้จังหวัดที่ user เลือก ถ้ามี — ไม่งั้นใช้ DEFAULT_LAT/LNG (0.0, 0.0)
        // (M1.b: Province.centroidLat/Lng แทน City.lat/lng)
        val province = selectedProvince
        val lat = province?.centroidLat ?: DEFAULT_LAT
        val lng = province?.centroidLng ?: DEFAULT_LNG
        val address = province?.let { "${it.nameTh} (จังหวัดที่เลือก)" }
            ?: "ที่อยู่ปัจจุบัน (ได้จาก GPS)"
        userLat = lat
        userLng = lng
        _state.value = LocationState.Granted(
            lat = lat,
            lng = lng,
            address = address,
            isFallback = true,
            fallbackReason = reason,
        )
    }

    private fun reverseGeocode(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val results = if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocation(lat, lng, 1)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)
            } ?: return null
            results.firstOrNull()?.let { addr ->
                addr.subAdminArea
                    ?: addr.locality
                    ?: addr.adminArea
                    ?: addr.featureName
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder failed", e)
            null
        }
    }

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "LocationRepository"

        // Default fallback — 0.0,0.0 (Phase 1)
        // Province selector จะ override ค่าเหล่านี้ตอน first launch / เมื่อ user เปลี่ยนจังหวัด
        // ใช้ 0.0 เพื่อให้ UI แสดง "ที่อยู่ปัจจุบัน (ได้จาก GPS)" แทน hardcode
        const val DEFAULT_LAT = 0.0
        const val DEFAULT_LNG = 0.0
    }
}

/**
 * Sealed class — location state machine.
 *
 * - [Idle]      : ยังไม่เริ่มหา (initial)
 * - [Loading]   : กำลังดึง fix (timeout 5s → fallback)
 * - [Granted]   : ได้ lat/lng (isFallback=true ถ้าใช้ default)
 * - [Denied]    : DEPRECATED — ใช้ Granted(isFallback=true) แทน เพื่อให้แอปรันได้เสมอ
 * - [Disabled]  : DEPRECATED — เหมือนกัน
 */
sealed class LocationState {
    data object Idle : LocationState()
    data object Loading : LocationState()
    data class Granted(
        val lat: Double,
        val lng: Double,
        val address: String? = null,
        val isFallback: Boolean = false,
        val fallbackReason: String? = null,
        /**
         * Unique identifier สำหรับแต่ละ fix — ใช้ timestamp ตอน applyLocation
         * ทำให้ทุก Granted instance ไม่ซ้ำกันใน [StateFlow] (distinct check ใช้ .equals())
         * เพื่อให้ collectLatest ใน TravelHomeViewModel ทำงานทุก fix แม้ lat/lng เท่าเดิม
         *
         * Default = 0L ตอน `Loading` หรือ emit ครั้งแรก — applyLocation จะใส่ค่าจริงตอนได้ fix
         */
        val fixId: Long = 0L,
    ) : LocationState()
}
