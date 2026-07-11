package com.thiengkin.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thiengkin.data.Restaurant
import com.thiengkin.data.RestaurantRepository
import com.thiengkin.ui.screens.travel.TravelHomeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sort modes บน Favorites screen
 *
 * - [Latest]    : เรียงตามชื่อ (proxy "เพิ่งเพิ่ม" เพราะ favoritedDaysAgo ยังเป็น null)
 *                 — Phase 1.5: เปลี่ยนเป็น favoritedAt timestamp
 * - [Province]  : เรียงตาม district
 * - [Rating]    : เรียงตาม rating desc
 */
enum class FavoritesSort(val label: String) {
    Latest("ล่าสุด"),
    Province("ตามจังหวัด"),
    Rating("ตาม rating"),
}

data class FavoritesState(
    val restaurants: List<Restaurant> = emptyList(),
    val sortMode: FavoritesSort = FavoritesSort.Latest,
    val emptyMessage: String = "ยังไม่มีรายการโปรด — แตะ ❤️ ที่ร้านเพื่อบันทึก",
)

/**
 * FavoritesViewModel
 *
 * Pipeline:
 *  1. observeFavorites() — list of is_favorite=1
 *  2. _sortMode — chip ที่เลือก
 *
 * → sort → FavoritesState
 */
class FavoritesViewModel(
    private val repository: RestaurantRepository = TravelHomeViewModel.defaultRepository,
) : ViewModel() {

    private val _sortMode = MutableStateFlow(FavoritesSort.Latest)

    val state: StateFlow<FavoritesState> = combine(
        repository.observeFavorites(),
        _sortMode,
    ) { favorites, mode ->
        val sorted: List<Restaurant> = when (mode) {
            FavoritesSort.Latest -> favorites.sortedBy { it.name }
            FavoritesSort.Province -> favorites.sortedWith(
                compareBy<Restaurant> { it.district ?: it.province ?: "อื่นๆ" }
                    .thenByDescending { it.rating ?: 0.0 }
            )
            FavoritesSort.Rating -> favorites.sortedByDescending { it.rating ?: 0.0 }
        }
        FavoritesState(
            restaurants = sorted,
            sortMode = mode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FavoritesState(),
    )

    fun setSortMode(mode: FavoritesSort) {
        _sortMode.value = mode
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { repository.toggleFavorite(id) }
    }
}
