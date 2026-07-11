package com.thiengkin.ui.screens.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thiengkin.data.LocationRepository
import com.thiengkin.data.LocationState
import com.thiengkin.data.Restaurant
import com.thiengkin.data.RestaurantRepository
import com.thiengkin.data.distanceMeters
import com.thiengkin.util.Haversine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TravelHomeState(
    val loading: Boolean = true,
    val restaurants: List<Restaurant> = emptyList(),
    val activeFilter: String = "ทั้งหมด",
    val location: LocationState = LocationState.Idle,
)

/**
 * TravelHomeViewModel
 *
 * Pipeline:
 *  1. observeTop(50) — top 50 by rating (มากพอให้ filter/sort แล้วยังเหลือ)
 *  2. _filter — chip ที่เลือก (ทั้งหมด / ริมทาง / เปิดเช้า / คนท้องถิ่น / ของฝาก)
 *  3. locationRepository.state — GPS
 *
 * → filter by tags → sort (distance if real GPS, else rating) → take(10)
 */
class TravelHomeViewModel(
    private val repository: RestaurantRepository = defaultRepository,
    private val locationRepository: LocationRepository = defaultLocationRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow("ทั้งหมด")

    val state: StateFlow<TravelHomeState> = combine(
        repository.observeTop(50),
        _filter,
        locationRepository.state,
    ) { all, filter, location ->
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
                    filtered.sortedByDescending { it.rating ?: 0.0 }
                }
            else -> filtered.sortedByDescending { it.rating ?: 0.0 }
        }

        // 3) Take top 10
        TravelHomeState(
            loading = false,
            restaurants = sorted.take(10),
            activeFilter = filter,
            location = location,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TravelHomeState(),
    )

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

    companion object {
        // ผูก repository จาก Application — Phase 1 ใช้ static ref ก่อน
        // Phase 1.5: เปลี่ยนเป็น Hilt
        lateinit var defaultRepository: RestaurantRepository
        lateinit var defaultLocationRepository: LocationRepository

        /**
         * Filter chip → tag mapping
         * เช็คทั้ง Thai tag และ English tag กันพลาด
         */
        val FILTER_TO_TAGS: Map<String, List<String>> = mapOf(
            "ริมทาง" to listOf("ริมทาง", "highway", "ริมปั๊ม"),
            "เปิดเช้า" to listOf("เปิดเช้า", "morning", "early"),
            "คนท้องถิ่น" to listOf("คนท้องถิ่น", "local_favorite", "local"),
            "ของฝาก" to listOf("ของฝาก", "souvenir"),
        )
    }
}
