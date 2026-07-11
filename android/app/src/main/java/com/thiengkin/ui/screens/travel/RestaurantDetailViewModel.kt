package com.thiengkin.ui.screens.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thiengkin.data.Restaurant
import com.thiengkin.data.RestaurantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RestaurantDetailState(
    val restaurant: Restaurant? = null,
    val loading: Boolean = true,
)

class RestaurantDetailViewModel(
    private val repository: RestaurantRepository = TravelHomeViewModel.defaultRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RestaurantDetailState())
    val state: StateFlow<RestaurantDetailState> = _state.asStateFlow()

    fun load(id: String) {
        viewModelScope.launch {
            val r = repository.getById(id)
            _state.value = RestaurantDetailState(restaurant = r, loading = false)
        }
    }

    /** Toggle favorite for the currently loaded restaurant. */
    fun toggleFavorite() {
        val current = _state.value.restaurant ?: return
        viewModelScope.launch {
            repository.toggleFavorite(current.id)
            // refresh local state
            val updated = repository.getById(current.id)
            if (updated != null) {
                _state.value = _state.value.copy(restaurant = updated)
            }
        }
    }
}
