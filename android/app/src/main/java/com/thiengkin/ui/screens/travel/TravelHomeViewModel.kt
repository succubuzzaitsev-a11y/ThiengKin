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
import com.thiengkin.data.SettingsRepository
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
import kotlinx.coroutines.flow.collectLatest

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
    val searchQuery: String = "",
    val location: LocationState = LocationState.Idle,
    val selectedProvince: Province? = null,
    val selectedDistrict: District? = null,
    val provinces: List<Province> = emptyList(),
    val districtsForSelectedProvince: List<District> = emptyList(),
    val refreshMessage: String? = null,  // แสดง toast/ข้อความหลัง refresh เสร็จ
    val selectedCategoryKey: String? = null,  // M2.1: หมวดที่เลือกจาก CategoryGrid (null = ทั้งหมด)
    val autoDetectEnabled: Boolean = true,  // M6 Phase 1: toggle GPS auto-detect province
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
    private val settingsRepository: SettingsRepository = defaultSettingsRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow("ทั้งหมด")
    private val _searchQuery = MutableStateFlow("")
    private val _selectedProvinceId = MutableStateFlow<String?>(null)
    private val _selectedDistrictId = MutableStateFlow<String?>(null)
    private val _refreshing = MutableStateFlow(false)
    private val _refreshMessage = MutableStateFlow<String?>(null)
    private val _selectedCategoryKey = MutableStateFlow<String?>(null)  // M2.1: CategoryGrid selection
    private val _autoDetectEnabled = MutableStateFlow(true)  // M6: default = true (เดิม)

    private var refreshJob: Job? = null

    /**
     * GPS auto-detect guard — เมื่อได้ real fix ครั้งแรกแล้ว จะ switch province อัตโนมัติ
     *
     * M6 Phase 1 behavior:
     * - ถ้า `_autoDetectEnabled.value == true` → reset flag เป็น false ทุกครั้งที่ได้ fix ใหม่
     *   → auto-detect ทุกครั้ง (default behavior)
     * - ถ้า `_autoDetectEnabled.value == false` → set flag เป็น true หลัง first detect
     *   → จำจังหวัดที่ user เลือก (ไม่ override)
     */
    private var gpsAutoDetectDone = false

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

    /** Data + filter pipeline (7 flows — M2.1: +selectedCategoryKey) */
    private val dataFlow = combine(
        restaurantsFlow,
        _filter,
        _searchQuery,
        locationRepository.state,
        _refreshing,
        _refreshMessage,
        _selectedCategoryKey,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val all = values[0] as List<Restaurant>
        val filter = values[1] as String
        val query = values[2] as String
        @Suppress("UNCHECKED_CAST")
        val location = values[3] as LocationState
        val refreshing = values[4] as Boolean
        val refreshMsg = values[5] as String?
        val catKey = values[6] as String?

        // 1) Search query (M4) — substring match name + nameTh + nameEn + category
        //    ทำก่อน chip filter เพราะ result set เล็กลง filter เร็วขึ้น
        val searched = if (query.isBlank()) {
            all
        } else {
            val q = query.trim()
            all.filter { r ->
                r.name.contains(q, ignoreCase = true) ||
                    (r.nameTh?.contains(q, ignoreCase = true) == true) ||
                    (r.category?.contains(q, ignoreCase = true) == true)
            }
        }

        // 2) Filter by chip (M4: predicate-based, รองรับทั้ง tags + category + openingHours)
        val filtered = if (filter == "ทั้งหมด" || filter.isBlank()) {
            searched
        } else {
            val predicate = FILTERS[filter] ?: FILTERS["ทั้งหมด"]!!
            searched.filter(predicate)
        }

        // 2.5) M2.1: Filter by CategoryGrid (multi-strategy: category + tags + name)
        //      catKey == null → no filter (ทั้งหมด)
        val catFiltered = if (catKey.isNullOrBlank()) {
            filtered
        } else {
            val predicate = CATEGORY_FILTERS[catKey]
            if (predicate != null) {
                filtered.filter(predicate)
            } else {
                // unknown key → no matches
                emptyList()
            }
        }

        // 3) Sort: real GPS → distance asc, fallback → rating desc
        val sorted: List<Restaurant> = when (location) {
            is LocationState.Granted ->
                if (!location.isFallback) {
                    catFiltered.sortedBy { r ->
                        Haversine.distanceKm(location.lat, location.lng, r.lat, r.lng)
                    }
                } else {
                    // Fallback (province centroid) — ใช้ rating + ระยะจาก province centroid
                    catFiltered.sortedWith(
                        compareByDescending<Restaurant> { it.rating ?: 0.0 }
                            .thenBy { r -> Haversine.distanceKm(location.lat, location.lng, r.lat, r.lng) }
                    )
                }
            else -> catFiltered.sortedByDescending { it.rating ?: 0.0 }
        }

        DataState(
            restaurants = sorted.take(20),
            filter = filter,
            query = query,
            location = location,
            refreshing = refreshing,
            refreshMsg = refreshMsg,
            catKey = catKey,
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
        _autoDetectEnabled,
    ) { data, lookup, autoDetect ->
        TravelHomeState(
            loading = false,
            refreshing = data.refreshing,
            restaurants = data.restaurants,
            activeFilter = data.filter,
            searchQuery = data.query,
            location = data.location,
            selectedProvince = lookup.selectedProvince,
            selectedDistrict = lookup.selectedDistrict,
            provinces = lookup.provinces,
            districtsForSelectedProvince = lookup.districts,
            refreshMessage = data.refreshMsg,
            selectedCategoryKey = data.catKey,  // M2.1
            autoDetectEnabled = autoDetect,  // M6 Phase 1
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TravelHomeState(),
    )

    init {
        // M5 fix v2: รอ geography seed เสร็จก่อนโหลด provinces + initial selection
        // (เดิม v1 race condition — `isEmpty()` exit เมื่อ province แรก insert เสร็จ
        //  → picker เห็น 1 จังหวัด จนกว่า ViewModel จะ reload)
        // v2: poll จนกว่าจะได้ 77 provinces ครบ (max 10s)
        viewModelScope.launch {
            var provinces = emptyList<Province>()
            var attempts = 0
            while (provinces.size < EXPECTED_PROVINCE_COUNT && attempts < 200) {  // max 10s (50ms × 200)
                provinces = provinceDao.getAll()
                if (provinces.size >= EXPECTED_PROVINCE_COUNT) break
                kotlinx.coroutines.delay(50)
                attempts++
            }
            _provinces.value = provinces
            Log.i(
                TAG,
                "Loaded ${provinces.size}/$EXPECTED_PROVINCE_COUNT provinces (after $attempts polls)",
            )

            // หลัง provinces พร้อม → re-read initial province (อาจเพิ่ง set โดย ThiengKinApp seed completion)
            if (_selectedProvinceId.value == null) {
                val initialProvince = locationRepository.getSelectedProvince()
                val initialDistrict = locationRepository.getSelectedDistrict()
                if (initialProvince != null) {
                    Log.i(
                        TAG,
                        "Initial province from LocationRepository: ${initialProvince.id} (${initialProvince.nameTh})",
                    )
                    _selectedProvinceId.value = initialProvince.id
                    _selectedDistrictId.value = initialDistrict?.id
                    _districts.value = districtDao.getByProvince(initialProvince.id)
                    triggerRefreshIfNeeded(initialProvince, initialDistrict?.id, force = false)
                } else {
                    Log.w(TAG, "No initial province — user must pick from picker")
                }
            }
        }

        // M4: GPS auto-detect → เมื่อได้ real fix ครั้งแรก ให้หา province ที่ใกล้ที่สุด
        //     (centroid distance) แล้ว switch ถ้าต่างจาก default — ทำครั้งเดียวต่อ session
        //
        // M6 Phase 1: behavior เปลี่ยนตาม toggle
        //   - autoDetectEnabled=true  → reset flag ทุก fix ใหม่ → re-detect ทุกครั้ง
        //   - autoDetectEnabled=false → set flag=true หลัง first detect → จำจังหวัดเดิม
        viewModelScope.launch {
            settingsRepository.autoDetectEnabled.collect { enabled ->
                _autoDetectEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            locationRepository.state.collectLatest { location ->
                if (location !is LocationState.Granted || location.isFallback) return@collectLatest
                if (_autoDetectEnabled.value) {
                    // M6: auto-detect ON → re-detect on every new fix
                    autoSelectProvinceFromGps(location.lat, location.lng)
                } else if (!gpsAutoDetectDone) {
                    // M6: auto-detect OFF → only first detect, then sticky
                    gpsAutoDetectDone = true
                    autoSelectProvinceFromGps(location.lat, location.lng)
                }
            }
        }
    }

    /**
     * M4: หา province ที่ใกล้ GPS fix ที่สุด (จาก centroid) แล้ว setProvince
     * - ถ้าใกล้กว่า default Bangkok (>X km) → switch
     * - ถ้าใกล้เดิม → no-op
     * - ถ้าไม่เจอเลย (empty list) → no-op
     */
    private suspend fun autoSelectProvinceFromGps(lat: Double, lng: Double) {
        val provinces = _provinces.value.ifEmpty { provinceDao.getAll() }
        if (provinces.isEmpty()) {
            Log.w(TAG, "GPS auto-detect skipped: no provinces loaded")
            return
        }
        val nearest = provinces.minByOrNull { p ->
            Haversine.distanceKm(lat, lng, p.centroidLat, p.centroidLng)
        } ?: return

        val current = _selectedProvinceId.value
        if (current == nearest.id) {
            Log.d(TAG, "GPS auto-detect: already at ${nearest.id} (${nearest.nameTh})")
            return
        }

        val distanceKm = Haversine.distanceKm(lat, lng, nearest.centroidLat, nearest.centroidLng)
        Log.i(
            TAG,
            "GPS auto-detect: lat=$lat lng=$lng → ${nearest.id} (${nearest.nameTh}) " +
                "distance=${"%.1f".format(distanceKm)}km (was=$current)",
        )
        // setProvince จะ trigger OSM fetch ใหม่สำหรับ province ใหม่
        setProvince(nearest, null)
    }

    fun setFilter(label: String) {
        _filter.value = label
    }

    /**
     * M2.1: เลือกหมวดหมู่จาก CategoryGrid
     * - key = "noodle" | "rice" | "cafe" | ... | null (toggle off)
     * - ถ้า key เดิม → toggle off (null)
     */
    fun selectCategory(key: String?) {
        _selectedCategoryKey.value = if (_selectedCategoryKey.value == key) null else key
    }

    /** M4: search query — substring filter on restaurant name + nameTh + category */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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
     * M6 Phase 1: เปิด/ปิด auto-detect province
     * - เมื่อ toggle เปลี่ยนจาก false→true ให้ reset flag → re-detect ทันที
     * - เมื่อ toggle เปลี่ยนจาก true→false ให้ set flag=true → ไม่ override จังหวัดที่ user เลือก
     */
    fun setAutoDetectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoDetectEnabled(enabled)
            _autoDetectEnabled.value = enabled
            if (enabled) {
                // Force re-detect on next fix
                gpsAutoDetectDone = false
            } else {
                // Sticky: remember current province, no more auto-override
                gpsAutoDetectDone = true
            }
        }
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
        // M5 fix: cancel previous job ก่อน (เดิม skip → user เปลี่ยนจังหวัดเร็วๆ จะตกหล่น)
        // ตอนนี้: cancel + launch ใหม่ทันที
        refreshJob?.cancel()
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                // M5: refresh job ถูก cancel (มีการเปลี่ยน province/district) — ไม่ต้องแสดง error
                Log.d(TAG, "refresh cancelled (newer refresh started)")
                throw e
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
        val query: String,
        val location: LocationState,
        val refreshing: Boolean,
        val refreshMsg: String?,
        val catKey: String?,  // M2.1
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
        lateinit var defaultSettingsRepository: SettingsRepository

        // v0.3: จำนวนจังหวัดที่ bundle ใน assets/thailand-geography.json (77 provinces)
        // ใช้เป็น target ในการ poll geography seed — เดิมใช้ isEmpty() เช็คแค่ "มีอย่างน้อย 1"
        // ทำให้ picker เห็น 1 จังหวัด (นนทบุรี) ตอน seed ยังไม่เสร็จ
        const val EXPECTED_PROVINCE_COUNT = 77

        /**
         * Filter chip → predicate (M4: predicate-based filter, ใช้ทั้ง tags + category + openingHours)
         *
         * OSM-derived data:
         * - `r.category` (string) = "ร้านอาหาร" | "คาเฟ่" | "ฟาสต์ฟู้ด" | "ศูนย์อาหาร" (mapped from `amenity` tag)
         * - `r.tags` (list) = OSM tags เช่น "cuisine:thai", "takeaway:yes", "outdoor_seating", etc.
         * - `r.openingHours` (string) = "Mo-Fr 07:00-22:00" (OSM standard format, 18% ของ places)
         *
         * **v0.3 (post-M5) design notes:**
         * - "เปิดเช้า" — parse opening_hours string เพื่อหา early-morning range (ก่อน 10:00)
         *   ก่อนหน้านี้ filter แค่ `openingHours != null` (ร้านที่ระบุเวลาเฉยๆ ไม่ใช่ "เปิดเช้า" จริง)
         * - "ร้านกาแฟ" — cafe + coffee_shop (เดิมชื่อ "ของฝาก" + รวม bubble_tea ที่ไม่ใช่กาแฟ)
         */
        val FILTERS: Map<String, (Restaurant) -> Boolean> = mapOf(
            "ทั้งหมด" to { true },
            // ริมทาง = fast food (ริมถนน) + takeaway (ซื้อกลับได้)
            "ริมทาง" to { r ->
                r.category == "ฟาสต์ฟู้ด" ||
                    r.tags.contains("takeaway:yes") ||
                    r.tags.contains("takeaway")
            },
            // เปิดเช้า = parse openingHours หา early-morning range (ก่อน 10:00)
            //   pattern: "Mo-Fr 07:00-22:00; Sa-Su 11:00-23:00" → split by ; ก่อน
            "เปิดเช้า" to { r ->
                val oh = r.openingHours ?: return@to false
                val timePattern = Regex("""(\d{1,2}):(\d{2})\s*[-–]\s*(\d{1,2}):(\d{2})""")
                oh.split(';').any { segment ->
                    timePattern.find(segment)?.let { match ->
                        val startHour = match.groupValues[1].toIntOrNull() ?: return@let false
                        startHour < 10
                    } ?: false
                }
            },
            // คนท้องถิ่น = Thai / regional / noodle (อาหารท้องถิ่น)
            "คนท้องถิ่น" to { r ->
                r.tags.contains("cuisine:thai") ||
                    r.tags.contains("cuisine:regional") ||
                    r.tags.contains("cuisine:noodle")
            },
            // ร้านกาแฟ = cafe + coffee_shop (ไม่รวม bubble_tea)
            "ร้านกาแฟ" to { r ->
                r.category == "คาเฟ่" ||
                    r.tags.contains("cuisine:coffee_shop")
            },
        )

        /**
         * M2.1: CategoryGrid filter — แต่ละ slot ใช้ multi-strategy
         * (category + tags + name substring) เพราะ OSM `category` field มีแค่ 4 ค่า
         * (ร้านอาหาร/คาเฟ่/ฟาสต์ฟู้ด/ศูนย์อาหาร) ไม่ครอบคลุม 10 slots
         *
         * Logic: substring match บน name + tags — เร็ว (in-memory), ไม่กระทบ schema
         *
         * ตัวอย่างจาก BKK+Nonthaburi (200+ ร้าน):
         * - noodle: name contains ก๋วยเตี๋ยว/บะหมี่/ราเมง/เฝอ (rich)
         * - cafe: r.category == "คาเฟ่" (direct)
         * - late: parse opening_hours "22:00-..." (18% coverage in OSM)
         * - papaya/salad: name-based (OSM ไม่ tag explicitly — rely on real shop names)
         */
        val CATEGORY_FILTERS: Map<String, (Restaurant) -> Boolean> = mapOf(
            "noodle" to { r ->
                val name = r.name
                name.contains("ก๋วยเตี๋ยว") ||
                    name.contains("บะหมี่") ||
                    name.contains("ราเมง") ||
                    name.contains("เฝอ") ||
                    r.tags.any { it.contains("noodle") }
            },
            "rice" to { r ->
                r.name.contains("ข้าว") &&
                    (r.tags.any { it.contains("cuisine:thai") } || r.category == "ร้านอาหาร")
            },
            "cafe" to { r ->
                r.category == "คาเฟ่" ||
                    r.tags.any { it.contains("coffee_shop") }
            },
            "fastfood" to { r ->
                r.category == "ฟาสต์ฟู้ด" ||
                    r.tags.any { it.contains("burger") || it.contains("pizza") || it.contains("fast_food") }
            },
            "bakery" to { r ->
                val name = r.name
                name.contains("เบเกอรี่") ||
                    name.contains("เค้ก") ||
                    name.contains("ขนมปัง") ||
                    name.contains("bakery", ignoreCase = true) ||
                    name.contains("cake", ignoreCase = true)
            },
            "papaya" to { r ->
                r.name.contains("ส้มตำ") ||
                    r.name.contains("ตำ") ||
                    r.name.contains("som tam", ignoreCase = true)
            },
            "salad" to { r ->
                r.name.contains("สลัด") ||
                    r.name.contains("salad", ignoreCase = true)
            },
            "pub" to { r ->
                val name = r.name
                name.contains("ผับ") ||
                    name.contains("บาร์") ||
                    name.contains("pub", ignoreCase = true) ||
                    name.contains("bar", ignoreCase = true) ||
                    r.tags.any { it == "amenity:pub" || it == "amenity:bar" || it == "amenity:nightclub" }
            },
            "dessert" to { r ->
                r.tags.any { it.contains("ice_cream") } ||
                    r.name.contains("ของหวาน") ||
                    r.name.contains("ไอศกรีม") ||
                    r.name.contains("dessert", ignoreCase = true)
            },
            "late" to { r ->
                val oh = r.openingHours ?: return@to false
                // OSM format: "Mo-Fr 07:00-22:00; Sa-Su 11:00-23:00"
                // หา segment ที่ close >= 22:00 หรือ < 03:00
                val timeRange = Regex("""(\d{1,2}):(\d{2})\s*[-–]\s*(\d{1,2}):(\d{2})""")
                oh.split(';').any { segment ->
                    timeRange.find(segment)?.let { match ->
                        val closeHour = match.groupValues[3].toIntOrNull() ?: 0
                        closeHour >= 22 || closeHour < 3
                    } ?: false
                }
            },
        )
    }
}
