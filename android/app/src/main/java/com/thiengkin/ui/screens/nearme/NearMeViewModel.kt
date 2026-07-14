package com.thiengkin.ui.screens.nearme

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

data class NearMeState(
    val restaurants: List<Restaurant> = emptyList(),
    val location: LocationState = LocationState.Idle,
    val radiusKm: Double = 3.0,
    val activeCategory: String = "ทั้งหมด",
)

/**
 * NearMeViewModel — หน้า "ใกล้ฉัน"
 *
 * Pipeline:
 *  1. repository.observeAll() — ทุกร้านใน DB
 *  2. locationRepository.state — ตำแหน่งปัจจุบัน (real GPS / fallback)
 *  3. _radius (3 / 5 / 10 กม.)
 *  4. _category (filter chip ที่เลือก)
 *
 * → combine → filter by category → filter by radius (Haversine จาก current loc)
 *   → sort by distance ascending → emit
 */
class NearMeViewModel(
    private val repository: RestaurantRepository = TravelHomeViewModel.defaultRepository,
    private val locationRepository: LocationRepository = TravelHomeViewModel.defaultLocationRepository,
) : ViewModel() {

    private val _radius = MutableStateFlow(3.0)
    private val _category = MutableStateFlow("ทั้งหมด")

    val state: StateFlow<NearMeState> = combine(
        repository.observeAll(),
        _radius,
        _category,
        locationRepository.state,
    ) { all, radius, cat, loc ->
        val (anchorLat, anchorLng) = when (loc) {
            is LocationState.Granted -> loc.lat to loc.lng
            else -> LocationRepository.DEFAULT_LAT to LocationRepository.DEFAULT_LNG
        }

        // 1) Filter by category (match category display name, slug, or tag)
        val byCategory = if (cat == "ทั้งหมด") all else all.filter { r ->
            r.category == cat ||
                r.categorySlug == cat ||
                r.tags.contains(cat)
        }

        // 2) Filter by radius from current location
        val byRadius = byCategory.filter {
            Haversine.distanceKm(anchorLat, anchorLng, it.lat, it.lng) <= radius
        }

        // 3) Sort by distance ascending
        val sorted = byRadius.sortedBy {
            Haversine.distanceKm(anchorLat, anchorLng, it.lat, it.lng)
        }

        NearMeState(
            restaurants = sorted,
            location = loc,
            radiusKm = radius,
            activeCategory = cat,
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

    fun toggleFavorite(id: String) {
        viewModelScope.launch { repository.toggleFavorite(id) }
    }

    fun requestLocation() = locationRepository.requestLocation()
    fun onPermissionDenied() = locationRepository.markDenied()
}
