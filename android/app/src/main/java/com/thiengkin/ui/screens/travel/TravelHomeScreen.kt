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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.thiengkin.ui.components.AdSlot
import com.thiengkin.ui.components.CategoryGrid
import com.thiengkin.ui.components.CompactRow
import com.thiengkin.ui.components.FilterChip
import com.thiengkin.ui.components.Pill
import com.thiengkin.ui.components.PillVariant
import com.thiengkin.ui.components.ProvincePicker
import com.thiengkin.ui.components.SearchInput
import com.thiengkin.ui.components.TopBarAvatar
import com.thiengkin.ui.components.TopBarAvatarVariant
import com.thiengkin.ui.theme.S1
import com.thiengkin.ui.theme.S2
import com.thiengkin.ui.theme.S3
import com.thiengkin.ui.theme.S4
import com.thiengkin.ui.theme.S7

/**
 * Screen 01 — Travel Home (Dark)
 *
 * v0.5 (2026-07-14): M2 redesign — apply home-redesign.html spec
 *  - Pill.Solid + Home icon
 *  - TopBarAvatar.Solid (gradient red)
 *  - SearchInput: pill 999dp + mic (showMic=true)
 *  - AdSlot (140dp dashed)
 *  - CategoryGrid (5×2 with real food photos from res/drawable/category_*)
 *  - CompactRow (76×76 + ETA tag + นำทาง + ♡)
 *
 * KEEP current: Location card, Province picker, Filter chips (text), Section header "ใกล้คุณ"
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = S4, vertical = S3),
        ) {
            // === item: TopBar (M2 — Pill.Solid + Home icon + TopBarAvatar.Solid) ===
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = S2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill(
                        text = "อร่อยวันนี้",
                        variant = PillVariant.Solid,
                        showDot = false,
                        leadingIcon = Icons.Filled.Home,
                    )
                    TopBarAvatar(
                        initials = "ส",
                        variant = TopBarAvatarVariant.Solid,
                    )
                }
            }

            // === item: Greeting ===
            item {
                Text(
                    text = "จะกินอะไรดีวันนี้?",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.padding(top = S2, bottom = S3),
                )
            }

            // === item: Location card (KEEP current) ===
            item {
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
                    autoDetectEnabled = state.autoDetectEnabled,
                    onAutoDetectToggle = { viewModel.setAutoDetectEnabled(it) },
                    modifier = Modifier.padding(bottom = S2),
                )
            }

            // === item: Province picker + refresh (KEEP current) ===
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = S2),
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
                            state.selectedProvince?.let { province -> viewModel.setProvince(province, district) }
                        },
                        modifier = Modifier.weight(1f),
                    )
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
            }

            // === item: Search input (M2 — pill 999dp + mic) ===
            item {
                SearchInput(
                    leadingIcon = Icons.Filled.Search,
                    placeholder = "ค้นหาร้าน หรือ เมนู...",
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    showMic = true,
                    onMicClick = {
                        // TODO M2.1: pipe เข้า SpeechRecognizer
                    },
                    modifier = Modifier.padding(bottom = S2),
                )
            }

            // === CHANGE: AdSlot (140dp dashed) ===
            item {
                AdSlot(
                    title = "พื้นที่โฆษณา",
                    subtitle = "320 × 140 · สำหรับพันธมิตรธุรกิจ",
                    modifier = Modifier.padding(bottom = S3),
                )
            }

            // === CHANGE: CategoryGrid (5×2 รูปจริง) ===
            item {
                Column(modifier = Modifier.padding(bottom = S3)) {
                    Text(
                        text = "หมวดหมู่",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = S2),
                    )
                    CategoryGrid(
                        selectedKey = state.selectedCategoryKey,  // M2.1: wire to filter
                        onItemClick = { key ->
                            viewModel.selectCategory(key)  // M2.1: toggle category filter
                        },
                    )
                }
            }

            // === item: Filter chips (KEEP current — text style) ===
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(S2),
                    contentPadding = PaddingValues(bottom = S3),
                ) {
                    items(
                        listOf("ทั้งหมด", "ริมทาง", "เปิดเช้า", "คนท้องถิ่น", "ร้านกาแฟ")
                    ) { label ->
                        FilterChip(
                            text = label,
                            active = state.activeFilter == label,
                            onClick = { viewModel.setFilter(label) },
                        )
                    }
                }
            }

            // === item: Section header (KEEP current) ===
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = S2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "ใกล้คุณ",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        // M7: show "X เปิดอยู่ตอนนี้" subtext
                        Text(
                            text = "${state.openNowCount} เปิดอยู่ตอนนี้",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
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
            }

            // === M7: hide-closed toggle (Travel Home ตัวอย่าง) ===
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = S1),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ซ่อนร้านปิดแล้ว",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Switch(
                        checked = state.hideClosed,
                        onCheckedChange = { viewModel.setHideClosed(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            // === items: Restaurant list (M2 — CompactRow 76×76 + ETA + นำทาง + ♡) ===
            when {
                state.loading -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = S7),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                state.restaurants.isEmpty() -> item {
                    EmptyState(
                        refreshing = state.refreshing,
                        hasSearchQuery = state.searchQuery.isNotBlank(),
                        onRefresh = { viewModel.refresh() },
                        onClearSearch = { viewModel.setSearchQuery("") },
                    )
                }
                else -> items(state.restaurants, key = { it.id }) { restaurant ->
                    CompactRow(
                        restaurant = restaurant,
                        distanceMeters = restaurant.distanceMeters,
                        onNavigate = {
                            onNavigate(restaurant.lat, restaurant.lng, restaurant.name)
                        },
                        onFavoriteToggle = { viewModel.toggleFavorite(restaurant.id) },
                        onClick = { onRestaurantClick(restaurant.id) },
                    )
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
 * Empty state — เมื่อยังไม่มีข้อมูล (cache ว่าง + fetch ยังไม่เสร็จ) หรือ search ไม่เจอ
 */
@Composable
private fun EmptyState(
    refreshing: Boolean,
    hasSearchQuery: Boolean,
    onRefresh: () -> Unit,
    onClearSearch: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = S7),
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
            } else if (hasSearchQuery) {
                Text(text = "🔍", style = MaterialTheme.typography.displayLarge)
                Text(
                    text = "ไม่พบร้านที่ค้นหา",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "ลองเปลี่ยนคำค้นหรือเลือกจังหวัดอื่น",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onClearSearch() }
                        .padding(horizontal = S4, vertical = S2),
                ) {
                    Text(
                        text = "ล้างคำค้น",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else {
                Text(text = "🍜", style = MaterialTheme.typography.displayLarge)
                Text(
                    text = "ยังไม่มีข้อมูลร้านอาหาร",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "แตะปุ่มรีเฟรชเพื่อดึงข้อมูล",
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

@Composable
private fun CurrentLocationCard(
    location: LocationState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    autoDetectEnabled: Boolean,
    onAutoDetectToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = S4, vertical = S3)
            .clickable { onRetry() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(S3)) {
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

            // M6 Phase 1: auto-detect toggle — เปิด/ปิด GPS re-detect province
            // แสดงเฉพาะตอนมี Granted (มี GPS จริงหรือ fallback) — ถ้า Idle/Loading ไม่แสดง
            if (location is LocationState.Granted) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(S3),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ตรวจจับจังหวัดอัตโนมัติ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (autoDetectEnabled) {
                                "เปิด = ทุกครั้งที่เปิดแอปจะสลับตาม GPS"
                            } else {
                                "ปิด = จำจังหวัดที่เลือกไว้"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoDetectEnabled,
                        onCheckedChange = onAutoDetectToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
}
