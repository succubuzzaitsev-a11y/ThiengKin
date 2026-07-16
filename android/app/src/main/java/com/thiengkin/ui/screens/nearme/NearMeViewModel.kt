package com.thiengkin.ui.screens.nearme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thiengkin.data.LocationRepository
import com.thiengkin.data.LocationState
import com.thiengkin.data.OpeningHoursParser
import com.thiengkin.data.Restaurant
import com.thiengkin.data.RestaurantRepository
import com.thiengkin.data.SettingsRepository
import com.thiengkin.ui.screens.travel.TravelHomeViewModel
import com.thiengkin.util.Haversine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NearMeState(
    val restaurants: List<Restaurant> = emptyList(),
    val allCount: Int = 0,           // before filters (debug + total available)
    val openNowCount: Int = 0,       // how many are open right now
    val location: LocationState = LocationState.Idle,
    val radiusKm: Double = 3.0,
    val activeCategory: String = "ทั้งหมด",
    val hideClosed: Boolean = false,  // M7: toggle — hide closed restaurants
)

/**
 * NearMeViewModel — หน้า "ใกล้ฉัน"
 *
 * Pipeline:
 *  1. repository.observeAll() — ทุกร้านใน DB
 *  2. locationRepository.state — ตำแหน่งปัจจุบัน (real GPS / fallback)
 *  3. _radius (3 / 5 / 10 กม.)
 *  4. _category (filter chip — predicate-based, match name + category + tag)
 *  5. _hideClosed (toggle — filter out restaurants ที่ปิดแล้ว)
 *
 * → combine → filter category → filter closed → filter radius → sort by distance → emit
 */
class NearMeViewModel(
    private val repository: RestaurantRepository = TravelHomeViewModel.defaultRepository,
    private val locationRepository: LocationRepository = TravelHomeViewModel.defaultLocationRepository,
    private val settingsRepository: SettingsRepository = TravelHomeViewModel.defaultSettingsRepository,
) : ViewModel() {

    private val _radius = MutableStateFlow(3.0)
    private val _category = MutableStateFlow("ทั้งหมด")
    // M7: hideClosed — observe shared SettingsRepository (shared with Travel Home)
    private val _hideClosed = settingsRepository.hideClosed
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_HIDE_CLOSED)

    val state: StateFlow<NearMeState> = combine(
        repository.observeAll(),
        _radius,
        _category,
        _hideClosed,
        locationRepository.state,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val all = values[0] as List<Restaurant>
        val radius = values[1] as Double
        val cat = values[2] as String
        val hideClosed = values[3] as Boolean
        val loc = values[4] as LocationState

        val (anchorLat, anchorLng) = when (loc) {
            is LocationState.Granted -> loc.lat to loc.lng
            else -> LocationRepository.DEFAULT_LAT to LocationRepository.DEFAULT_LNG
        }

        // 1) Filter by category — predicate-based (match name substring OR category field)
        //    เพราะ SerpApi/OSM เก็บ category="ร้านอาหาร" (generic) แต่ชื่อร้านมีคำว่า "ก๋วยเตี๋ยว"/"กาแฟ"/etc.
        val byCategory = if (cat == "ทั้งหมด") all else all.filter { r ->
            CATEGORY_PREDICATES[cat]?.invoke(r) ?: (r.category == cat)
        }

        // 2) Filter by open/closed (M7: OpeningHoursParser)
        val byOpenStatus = if (hideClosed) {
            byCategory.filter { OpeningHoursParser.isOpenNow(it.openingHours) }
        } else {
            byCategory
        }

        // 3) Filter by radius from current location
        val byRadius = byOpenStatus.filter {
            Haversine.distanceKm(anchorLat, anchorLng, it.lat, it.lng) <= radius
        }

        // 4) Sort by distance ascending
        val sorted = byRadius.sortedBy {
            Haversine.distanceKm(anchorLat, anchorLng, it.lat, it.lng)
        }

        // Stats: count open (before hideClosed filter)
        val openCount = byCategory.count { OpeningHoursParser.isOpenNow(it.openingHours) }

        NearMeState(
            restaurants = sorted,
            allCount = byCategory.size,
            openNowCount = openCount,
            location = loc,
            radiusKm = radius,
            activeCategory = cat,
            hideClosed = hideClosed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NearMeState(),
    )

    fun setRadius(km: Double) {
        _radius.value = km
    }

    fun setCategory(category: String) {
        _category.value = category
    }

    /** M7: toggle hide-closed (shared with Travel Home via SettingsRepository) */
    fun setHideClosed(hide: Boolean) {
        viewModelScope.launch { settingsRepository.setHideClosed(hide) }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { repository.toggleFavorite(id) }
    }

    fun requestLocation() = locationRepository.requestLocation()
    fun onPermissionDenied() = locationRepository.markDenied()

    companion object {
        /**
         * Category chip → predicate (match name substring + category field)
         * Same approach as TravelHomeViewModel.FILTERS — works with SerpApi data
         * ที่เก็บ category="ร้านอาหาร" generic แต่ชื่อร้านมี keyword
         */
        val CATEGORY_PREDICATES: Map<String, (Restaurant) -> Boolean> = mapOf(
            "ก๋วยเตี๋ยว" to { r ->
                r.category == "ร้านอาหาร" && (
                    r.name.contains("ก๋วยเตี๋ยว") ||
                    r.name.contains("บะหมี่") ||
                    r.name.contains("ราเมง") ||
                    r.name.contains("เฝอ") ||
                    r.name.contains("boat noodle", ignoreCase = true) ||
                    r.tags.any { it.contains("noodle") || it.contains("ramen") }
                )
            },
            "ข้าวราดแกง" to { r ->
                r.category == "ร้านอาหาร" && (
                    r.name.contains("ข้าว") ||
                    r.name.contains("khao", ignoreCase = true) ||
                    r.name.contains("rice", ignoreCase = true) ||
                    r.tags.any { it.contains("rice") }
                )
            },
            "กาแฟ" to { r ->
                r.category == "คาเฟ่" ||
                    r.name.contains("กาแฟ") ||
                    r.name.contains("coffee", ignoreCase = true) ||
                    r.name.contains("cafe", ignoreCase = true) ||
                    r.tags.any { it.contains("coffee") || it.contains("cafe") }
            },
            "เปิดดึก" to { r ->
                // เปิดหลัง 22:00 หรือมีชื่อบอก
                r.name.contains("บาร์") || r.name.contains("bar", ignoreCase = true) ||
                    r.name.contains("ผับ") || r.name.contains("pub", ignoreCase = true) ||
                    r.tags.any { it.contains("bar") || it.contains("pub") || it.contains("nightclub") } ||
                    // OR มี opening_hours ที่ปิดดึก
                    OpeningHoursParser.formatForDisplay(r.openingHours)?.let { hours ->
                        hours.contains("22:") || hours.contains("23:") || hours.contains("00:") || hours.contains("01:") || hours.contains("02:")
                    } ?: false
            },
        )
    }
}
