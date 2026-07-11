package com.thiengkin.ui.screens.travel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thiengkin.data.City
import com.thiengkin.data.LocationRepository
import com.thiengkin.data.LocationState
import com.thiengkin.data.Restaurant
import com.thiengkin.data.RestaurantRepository
import com.thiengkin.data.distanceMeters
import com.thiengkin.util.Haversine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TravelHomeState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val restaurants: List<Restaurant> = emptyList(),
    val activeFilter: String = "ทั้งหมด",
    val location: LocationState = LocationState.Idle,
    val selectedCity: City? = null,
    val refreshMessage: String? = null,  // แสดง toast/ข้อความหลัง refresh เสร็จ
)

/**
 * TravelHomeViewModel
 *
 * Pipeline (Phase 2):
 *  1. selectedCityId — city ที่ user เลือก
 *  2. observeByCity(cityId) — Flow<List<Restaurant>> (manual + osm + foursquare)
 *  3. _filter — chip ที่เลือก
 *  4. locationRepository.state — GPS
 *
 * → filter by tags → sort (distance if real GPS, else rating) → take(10)
 *
 * Side effects:
 *  - onCityChange: trigger OSM/FSQ fetch (1×, มี cache TTL 7 วัน)
 *  - refresh(): force re-fetch
 */
class TravelHomeViewModel(
    private val repository: RestaurantRepository = defaultRepository,
    private val locationRepository: LocationRepository = defaultLocationRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow("ทั้งหมด")
    private val _selectedCityId = MutableStateFlow<String?>(null)
    private val _refreshing = MutableStateFlow(false)
    private val _refreshMessage = MutableStateFlow<String?>(null)

    private var refreshJob: Job? = null

    /**
     * observeByCity — เปลี่ยนตาม city ที่เลือก
     * flatMapLatest: ถ้า city เปลี่ยนกลาง stream, cancel stream เก่าแล้ว subscribe ใหม่
     */
    private val restaurantsFlow = _selectedCityId.flatMapLatest { cityId ->
        if (cityId == null) flowOf(emptyList())
        else repository.observeByCity(cityId)
    }

    val state: StateFlow<TravelHomeState> = combine(
        restaurantsFlow,
        _filter,
        locationRepository.state,
        _refreshing,
        _refreshMessage,
    ) { all, filter, location, refreshing, refreshMsg ->
        // 1) Filter by chip
        val filtered = if (filter == "ทั้งหมด") {
            all
        } else {
            val tags = FILTER_TO_TAGS[filter].orEmpty()
            all.filter { r -> tags.any { r.tags.contains(it) } }
        }

        // 2) Sort: real GPS → distance asc, fallback → rating desc
        val sorted: List<Restaurant> = when (location) {
            is LocationState.Granted ->
                if (!location.isFallback) {
                    filtered.sortedBy { r ->
                        Haversine.distanceKm(location.lat, location.lng, r.lat, r.lng)
                    }
                } else {
                    // Fallback (city center) — ใช้ rating + ระยะจาก city center
                    filtered.sortedWith(
                        compareByDescending<Restaurant> { it.rating ?: 0.0 }
                            .thenBy { r -> Haversine.distanceKm(location.lat, location.lng, r.lat, r.lng) }
                    )
                }
            else -> filtered.sortedByDescending { it.rating ?: 0.0 }
        }

        TravelHomeState(
            loading = false,
            refreshing = refreshing,
            restaurants = sorted.take(20),
            activeFilter = filter,
            location = location,
            selectedCity = locationRepository.getSelectedCity(),
            refreshMessage = refreshMsg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TravelHomeState(),
    )

    init {
        // Set initial city from LocationRepository
        locationRepository.getSelectedCity()?.let { city ->
            _selectedCityId.value = city.id
            triggerRefreshIfNeeded(city, force = false)
        }
    }

    fun setFilter(label: String) {
        _filter.value = label
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { repository.toggleFavorite(id) }
    }

    /** Trigger location request — call from MainActivity after permission grant. */
    fun requestLocation() {
        locationRepository.requestLocation()
    }

    fun onPermissionDenied() {
        locationRepository.markDenied()
    }

    /**
     * เปลี่ยนเมือง → trigger OSM/FSQ fetch
     */
    fun setCity(city: City) {
        Log.i(TAG, "setCity: ${city.id} (${city.nameTh})")
        locationRepository.setSelectedCity(city)
        _selectedCityId.value = city.id
        triggerRefreshIfNeeded(city, force = false)
    }

    /** Force refresh — ลบ cache + fetch ใหม่ */
    fun refresh() {
        val city = locationRepository.getSelectedCity() ?: return
        Log.i(TAG, "refresh: force city=${city.id}")
        triggerRefreshIfNeeded(city, force = true)
    }

    /** Clear refresh message (เรียกหลังแสดง toast แล้ว) */
    fun clearRefreshMessage() {
        _refreshMessage.value = null
    }

    private fun triggerRefreshIfNeeded(city: City, force: Boolean) {
        // Skip ถ้ามี refresh job กำลังรันอยู่
        if (refreshJob?.isActive == true) {
            Log.d(TAG, "refresh already in progress, skip")
            return
        }
        refreshJob = viewModelScope.launch {
            _refreshing.value = true
            try {
                val result = repository.refreshCity(city, force = force)
                _refreshMessage.value = if (result.skipped) {
                    result.reason ?: "ข้ามการดึงข้อมูล"
                } else {
                    "อัปเดตข้อมูลสำเร็จ (${result.osmCount} ร้าน)"
                }
                Log.i(TAG, "refresh done: $result")
            } catch (e: Exception) {
                Log.w(TAG, "refresh failed", e)
                _refreshMessage.value = "ดึงข้อมูลไม่สำเร็จ: ${e.message}"
            } finally {
                _refreshing.value = false
            }
        }
    }

    companion object {
        private const val TAG = "TravelHomeVM"

        // ผูก repository จาก Application — Phase 1 ใช้ static ref ก่อน
        // Phase 1.5: เปลี่ยนเป็น Hilt
        lateinit var defaultRepository: RestaurantRepository
        lateinit var defaultLocationRepository: LocationRepository

        /**
         * Filter chip → tag mapping
         * เช็คทั้ง Thai tag และ English tag + OSM-derived tag กันพลาด
         */
        val FILTER_TO_TAGS: Map<String, List<String>> = mapOf(
            "ริมทาง" to listOf("ริมทาง", "highway", "ริมปั๊ม"),
            "เปิดเช้า" to listOf("เปิดเช้า", "morning", "early", "opening_hours"),
            "คนท้องถิ่น" to listOf("คนท้องถิ่น", "local_favorite", "local", "cuisine:thai", "cuisine:noodle"),
            "ของฝาก" to listOf("ของฝาก", "souvenir"),
        )
    }
}