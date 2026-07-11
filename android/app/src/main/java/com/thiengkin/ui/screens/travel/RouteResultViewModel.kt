package com.thiengkin.ui.screens.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thiengkin.data.LocationRepository
import com.thiengkin.data.LocationState
import com.thiengkin.data.Restaurant
import com.thiengkin.data.RestaurantRepository
import com.thiengkin.ui.screens.travel.TravelHomeViewModel
import com.thiengkin.util.Haversine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Filter / sort chips บน RouteResult screen
 *
 * - [แนะนำ]       : default · เรียงตาม rating (แนะนำที่สุดก่อน)
 * - [คนท้องถิ่น]  : filter tags contains "local_favorite" · เรียงตาม rating
 * - [เปิดอยู่]     : filter category in เปิดเช้า/ตลาด/ก๋วยเตี๋ยวเช้า · (mock — Phase 1)
 * - [ใกล้ที่สุด]   : เรียงตาม distance asc (ใช้ Haversine)
 */
enum class RouteFilter(val label: String, val tag: String? = null) {
    Recommended("แนะนำ"),
    Local("คนท้องถิ่น", "local_favorite"),
    OpenNow("เปิดอยู่"),
    Nearest("ใกล้ที่สุด"),
}

data class RouteResultState(
    val loading: Boolean = true,
    val restaurants: List<Restaurant> = emptyList(),
    val activeFilter: RouteFilter = RouteFilter.Recommended,
    val location: LocationState = LocationState.Idle,
)

/**
 * RouteResultViewModel
 *
 * Pipeline:
 *  1. observeManualPicks() — 35 ร้านที่คัดสรร (มี tags ครบ)
 *  2. _filter — chip ที่เลือก (default แนะนำ)
 *  3. locationRepository.state — GPS
 *
 * → filter by chip → sort (rating / distance) → take(5)
 */
class RouteResultViewModel(
    private val repository: RestaurantRepository = TravelHomeViewModel.defaultRepository,
    private val locationRepository: LocationRepository = TravelHomeViewModel.defaultLocationRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(RouteFilter.Recommended)

    val state: StateFlow<RouteResultState> = combine(
        repository.observeManualPicks(),
        _filter,
        locationRepository.state,
    ) { picks, filter, location ->
        // 1) Filter by chip
        val filtered = when (filter) {
            RouteFilter.Recommended,
            RouteFilter.Nearest -> picks  // no filter, just sort
            RouteFilter.Local -> picks.filter { r -> r.tags.contains(filter.tag) }
            RouteFilter.OpenNow -> picks.filter { r ->
                r.tags.contains("morning") ||
                    r.tags.contains("early_open") ||
                    r.tags.contains("market")
            }
        }

        // 2) Sort: rating desc (default) | distance asc (ใกล้ที่สุด + มี GPS)
        val sorted: List<Restaurant> = if (filter == RouteFilter.Nearest &&
            location is LocationState.Granted && !location.isFallback
        ) {
            filtered.sortedBy { r ->
                Haversine.distanceKm(location.lat, location.lng, r.lat, r.lng)
            }
        } else {
            filtered.sortedByDescending { it.rating ?: 0.0 }
        }

        RouteResultState(
            loading = false,
            restaurants = sorted.take(5),
            activeFilter = filter,
            location = location,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RouteResultState(),
    )

    fun setFilter(filter: RouteFilter) {
        _filter.value = filter
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { repository.toggleFavorite(id) }
    }
}
