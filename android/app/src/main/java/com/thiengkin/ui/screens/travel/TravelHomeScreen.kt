package com.thiengkin.ui.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thiengkin.data.LocationState
import com.thiengkin.data.distanceMeters
import com.thiengkin.data.etaMinutes
import com.thiengkin.ui.components.FilterChip
import com.thiengkin.ui.components.Pill
import com.thiengkin.ui.components.PillVariant
import com.thiengkin.ui.components.ProvincePicker
import com.thiengkin.ui.components.RestaurantCard
import com.thiengkin.ui.components.SearchInput
import com.thiengkin.ui.theme.S1
import com.thiengkin.ui.theme.S2
import com.thiengkin.ui.theme.S3
import com.thiengkin.ui.theme.S4

/**
 * Screen 01 — Travel Home (Dark)
 *
 * Hero: location-aware card (current district/city) + restaurant list
 *
 * Phase 1: ไม่มี route detection — แสดง "ร้านใกล้คุณ" เรียงตาม rating
 * Phase 1.5: เพิ่ม route detection (current location → destination on map)
 * Phase 2: เพิ่ม OSM Overpass + Foursquare Free (city-scoped) + refresh button
 */
@Composable
fun TravelHomeScreen(
    onRestaurantClick: (String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit,
    onRouteClick: () -> Unit = {},
    onRequestLocationPermission: () -> Unit = {},
    viewModel: TravelHomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Show refresh message via Snackbar
    LaunchedEffect(state.refreshMessage) {
        state.refreshMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearRefreshMessage()
        }
    }

    // Auto-request location on first composition
    LaunchedEffect(Unit) {
        onRequestLocationPermission()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = S4),
        ) {
            // Top: pill + avatar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = S3, bottom = S2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(text = "อร่อยวันนี้", variant = PillVariant.Red)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("ส", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.background)
                }
            }

            // Greeting
            Text(
                text = "จะกินอะไรดีวันนี้?",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )

            // Location card
            CurrentLocationCard(
                location = state.location,
                onRequestPermission = onRequestLocationPermission,
                onRetry = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.requestLocation()
                    } else {
                        onRequestLocationPermission()
                    }
                },
                modifier = Modifier.padding(top = S3),
            )

            // Province/District selector + refresh button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = S2),
                horizontalArrangement = Arrangement.spacedBy(S2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProvincePicker(
                    selectedProvince = state.selectedProvince,
                    selectedDistrict = state.selectedDistrict,
                    provinces = state.provinces,
                    districtsForSelectedProvince = state.districtsForSelectedProvince,
                    onProvinceSelected = { viewModel.setProvince(it, null) },
                    onDistrictSelected = { district ->
                        // selectedProvince guaranteed non-null here: picker only shows district list after province selected
                        state.selectedProvince?.let { province -> viewModel.setProvince(province, district) }
                    },
                    modifier = Modifier.weight(1f),
                )
                // Refresh button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(enabled = !state.refreshing) { viewModel.refresh() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "รีเฟรชข้อมูลจังหวัด",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Search input
            SearchInput(
                leadingIcon = Icons.Filled.Search,
                placeholder = "ค้นหาร้าน...",
                showArrow = false,
                modifier = Modifier.padding(top = S3, bottom = S2),
            )

            // Filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(S2),
                contentPadding = PaddingValues(bottom = S3),
            ) {
                items(
                    listOf("ทั้งหมด", "ริมทาง", "เปิดเช้า", "คนท้องถิ่น", "ของฝาก")
                ) { label ->
                    FilterChip(
                        text = label,
                        active = state.activeFilter == label,
                        onClick = { viewModel.setFilter(label) },
                    )
                }
            }

            // Section label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = S2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ใกล้คุณ",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "ดูจุดแวะ →",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onRouteClick() }
                        .padding(horizontal = S2 + 2.dp, vertical = S1 + 2.dp),
                )
            }

            // Restaurant list
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (state.restaurants.isEmpty()) {
                EmptyState(
                    refreshing = state.refreshing,
                    onRefresh = { viewModel.refresh() },
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(S2)) {
                    items(state.restaurants, key = { it.id }) { restaurant ->
                        RestaurantCard(
                            restaurant = restaurant,
                            etaText = etaTextFor(restaurant.etaMinutes),
                            distText = distTextFor(restaurant.distanceMeters),
                            onNavigate = {
                                onNavigate(restaurant.lat, restaurant.lng, restaurant.name)
                            },
                            onFavoriteToggle = { viewModel.toggleFavorite(restaurant.id) },
                            onClick = { onRestaurantClick(restaurant.id) },
                        )
                    }
                }
            }
        }

        // Snackbar host (bottom)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Empty state — เมื่อยังไม่มีข้อมูล (cache ว่าง + fetch ยังไม่เสร็จ)
 */
@Composable
private fun EmptyState(
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(S3),
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = "กำลังดึงข้อมูลร้านอาหารจาก OSM...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "🍜",
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    text = "ยังไม่มีข้อมูลร้านอาหาร",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "แตะปุ่มรีเฟรชเพื่อดึงข้อมูลจาก OpenStreetMap",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onRefresh() }
                        .padding(horizontal = S4, vertical = S2),
                ) {
                    Text(
                        text = "รีเฟรช",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/**
 * Location card — แสดงสถานะปัจจุบันของ location (replaces hardcoded route)
 *
 * - Idle: "แตะเพื่อระบุตำแหน่ง"
 * - Loading: spinner + "กำลังระบุตำแหน่ง..."
 * - Granted (real): "📍 {address}" + lat/lng
 * - Granted (fallback): "📍 ที่อยู่ปัจจุบัน (ได้จาก GPS)" + "แตะเพื่อลอง GPS จริง"
 */
@Composable
private fun CurrentLocationCard(
    location: LocationState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = S4, vertical = S3)
            .clickable {
                when (location) {
                    is LocationState.Idle, is LocationState.Loading -> onRetry()
                    is LocationState.Granted -> {
                        // Tap to retry real GPS — works for both real & fallback
                        onRetry()
                    }
                }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(S3),
        ) {
            when (location) {
                is LocationState.Idle -> {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "แตะเพื่อระบุตำแหน่ง",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is LocationState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "กำลังระบุตำแหน่ง...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is LocationState.Granted -> {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = if (location.isFallback)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = location.address ?: "ตำแหน่งปัจจุบัน",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        if (location.isFallback) {
                            Text(
                                text = location.fallbackReason
                                    ?: "แตะเพื่อลอง GPS จริง",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        } else {
                            Text(
                                text = "%.4f, %.4f".format(location.lat, location.lng),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun etaTextFor(eta: Int?): String =
    if (eta != null) "อีก $eta นาที" else "—"

private fun distTextFor(meters: Int?): String =
    if (meters != null) {
        if (meters < 1000) "$meters ม." else "%.1f กม.".format(meters / 1000.0)
    } else {
        "—"
    }
