package com.thiengkin.ui.screens.travel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thiengkin.data.District
import com.thiengkin.data.DistrictDao
import com.thiengkin.data.LocationRepository
import com.thiengkin.data.LocationState
import com.thiengkin.data.Province
import com.thiengkin.data.ProvinceDao
import com.thiengkin.data.Restaurant
import com.thiengkin.data.RestaurantRepository
import com.thiengkin.data.distanceMeters
import com.thiengkin.data.toBoundingBox
import com.thiengkin.util.Haversine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * TravelHomeState — UI state ทั้งหมดของ Travel Home screen
 *
 * M1.b: เพิ่ม selectedProvince + selectedDistrict + provinces (all 77) + districtsForSelectedProvince
 *       (มาจาก ProvinceDao/DistrictDao) — เพื่อให้ ProvincePicker ทำงาน
 */
data class TravelHomeState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val restaurants: List<Restaurant> = emptyList(),
    val activeFilter: String = "ทั้งหมด",
    val location: LocationState = LocationState.Idle,
    val selectedProvince: Province? = null,
    val selectedDistrict: District? = null,
    val provinces: List<Province> = emptyList(),
    val districtsForSelectedProvince: List<District> = emptyList(),
    val refreshMessage: String? = null,  // แสดง toast/ข้อความหลัง refresh เสร็จ
)

/**
 * TravelHomeViewModel
 *
 * Pipeline (M1.b — nationwide):
 *  1. selectedProvinceId (+ optional selectedDistrictId) — จังหวัด/อำเภอ ที่ user เลือก
 *  2. observeByProvince / observeByDistrict — Flow<List<Restaurant>>
 *  3. _filter — chip ที่เลือก
 *  4. locationRepository.state — GPS
 *  5. provinces / districts (จาก DAOs) — ใช้ใน ProvincePicker
 *
 * → filter by tags → sort (distance if real GPS, else rating) → take(20)
 *
 * Side effects:
 *  - setProvince: trigger OSM/FSQ fetch (1×, มี cache TTL 7 วัน)
 *  - refresh(): force re-fetch
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TravelHomeViewModel(
    private val repository: RestaurantRepository = defaultRepository,
    private val locationRepository: LocationRepository = defaultLocationRepository,
    private val provinceDao: ProvinceDao = defaultProvinceDao,
    private val districtDao: DistrictDao = defaultDistrictDao,
) : ViewModel() {

    private val _filter = MutableStateFlow("ทั้งหมด")
    private val _selectedProvinceId = MutableStateFlow<String?>(null)
    private val _selectedDistrictId = MutableStateFlow<String?>(null)
    private val _refreshing = MutableStateFlow(false)
    private val _refreshMessage = MutableStateFlow<String?>(null)

    private var refreshJob: Job? = null

    /** All 77 provinces — load ครั้งเดียว เก็บใน memory ให้ ProvincePicker ใช้ */
    private val _provinces = MutableStateFlow<List<Province>>(emptyList())

    /** Districts ของจังหวัดที่เลือก — load เมื่อ user เลือกจังหวัด */
    private val _districts = MutableStateFlow<List<District>>(emptyList())

    /**
     * Restaurant flow — เปลี่ยนตาม province/district ที่เลือก
     * flatMapLatest: ถ้า selection เปลี่ยนกลาง stream, cancel stream เก่าแล้ว subscribe ใหม่
     */
    private val restaurantsFlow = combine(
        _selectedProvinceId,
        _selectedDistrictId,
    ) { provinceId, districtId -> provinceId to districtId }
        .flatMapLatest { (provinceId, districtId) ->
            when {
                provinceId == null -> flowOf(emptyList())
                districtId != null -> repository.observeByDistrict(districtId)
                else -> repository.observeByProvince(provinceId)
            }
        }

    /** Data + filter pipeline (5 flows) */
    private val dataFlow = combine(
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
                    // Fallback (province centroid) — ใช้ rating + ระยะจาก province centroid
                    filtered.sortedWith(
                        compareByDescending<Restaurant> { it.rating ?: 0.0 }
                            .thenBy { r -> Haversine.distanceKm(location.lat, location.lng, r.lat, r.lng) }
                    )
                }
            else -> filtered.sortedByDescending { it.rating ?: 0.0 }
        }

        DataState(
            restaurants = sorted.take(20),
            filter = filter,
            location = location,
            refreshing = refreshing,
            refreshMsg = refreshMsg,
        )
    }

    /** Lookup pipeline (4 flows): provinces + districts + selected ids */
    private val lookupFlow = combine(
        _provinces,
        _districts,
        _selectedProvinceId,
        _selectedDistrictId,
    ) { provinces, districts, provinceId, districtId ->
        LookupState(
            selectedProvince = provinces.firstOrNull { it.id == provinceId },
            selectedDistrict = districts.firstOrNull { it.id == districtId },
            provinces = provinces,
            districts = districts,
        )
    }

    val state: StateFlow<TravelHomeState> = combine(
        dataFlow,
        lookupFlow,
    ) { data, lookup ->
        TravelHomeState(
            loading = false,
            refreshing = data.refreshing,
            restaurants = data.restaurants,
            activeFilter = data.filter,
            location = data.location,
            selectedProvince = lookup.selectedProvince,
            selectedDistrict = lookup.selectedDistrict,
            provinces = lookup.provinces,
            districtsForSelectedProvince = lookup.districts,
            refreshMessage = data.refreshMsg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TravelHomeState(),
    )

    init {
        // Load all provinces (once, kept in memory for picker)
        viewModelScope.launch {
            _provinces.value = provinceDao.getAll()
        }

        // Set initial province + district from LocationRepository
        val initialProvince = locationRepository.getSelectedProvince()
        val initialDistrict = locationRepository.getSelectedDistrict()
        if (initialProvince != null) {
            _selectedProvinceId.value = initialProvince.id
            _selectedDistrictId.value = initialDistrict?.id
            viewModelScope.launch {
                _districts.value = districtDao.getByProvince(initialProvince.id)
                triggerRefreshIfNeeded(initialProvince, initialDistrict?.id, force = false)
            }
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
     * เปลี่ยนจังหวัด (+ optional อำเภอ) → trigger OSM/FSQ fetch
     */
    fun setProvince(province: Province, district: District? = null) {
        Log.i(TAG, "setProvince: ${province.id} (${province.nameTh}) district=${district?.id}")
        locationRepository.setSelectedProvince(province, district)
        _selectedProvinceId.value = province.id
        _selectedDistrictId.value = district?.id
        viewModelScope.launch {
            _districts.value = districtDao.getByProvince(province.id)
        }
        triggerRefreshIfNeeded(province, district?.id, force = false)
    }

    /** Force refresh — ลบ cache + fetch ใหม่ */
    fun refresh() {
        val provinceId = _selectedProvinceId.value ?: return
        val province = _provinces.value.firstOrNull { it.id == provinceId } ?: return
        val districtId = _selectedDistrictId.value
        Log.i(TAG, "refresh: force province=${province.id} district=$districtId")
        triggerRefreshIfNeeded(province, districtId, force = true)
    }

    /** Clear refresh message (เรียกหลังแสดง toast แล้ว) */
    fun clearRefreshMessage() {
        _refreshMessage.value = null
    }

    private fun triggerRefreshIfNeeded(province: Province, districtId: String?, force: Boolean) {
        // Skip ถ้ามี refresh job กำลังรันอยู่
        if (refreshJob?.isActive == true) {
            Log.d(TAG, "refresh already in progress, skip")
            return
        }
        refreshJob = viewModelScope.launch {
            _refreshing.value = true
            try {
                val district = districtId?.let { id -> _districts.value.firstOrNull { it.id == id } }
                val bbox = district?.toBoundingBox() ?: province.toBoundingBox()
                val result = repository.refreshArea(
                    provinceId = province.id,
                    districtId = districtId,
                    bbox = bbox,
                    force = force,
                )
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

    private data class DataState(
        val restaurants: List<Restaurant>,
        val filter: String,
        val location: LocationState,
        val refreshing: Boolean,
        val refreshMsg: String?,
    )

    private data class LookupState(
        val selectedProvince: Province?,
        val selectedDistrict: District?,
        val provinces: List<Province>,
        val districts: List<District>,
    )

    companion object {
        private const val TAG = "TravelHomeVM"

        // ผูก DAOs/Repositories จาก Application — Phase 1 ใช้ static ref ก่อน
        // Phase 1.5: เปลี่ยนเป็น Hilt
        lateinit var defaultRepository: RestaurantRepository
        lateinit var defaultLocationRepository: LocationRepository
        lateinit var defaultProvinceDao: ProvinceDao
        lateinit var defaultDistrictDao: DistrictDao

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
