package com.thiengkin.ui.screens.nearme

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thiengkin.data.LocationState
import com.thiengkin.data.distanceMeters
import com.thiengkin.ui.components.FilterChip
import com.thiengkin.ui.components.Pill
import com.thiengkin.ui.components.PillVariant
import com.thiengkin.ui.components.RestaurantCard
import com.thiengkin.ui.components.SearchInput
import com.thiengkin.ui.theme.S1
import com.thiengkin.ui.theme.S2
import com.thiengkin.ui.theme.S3
import com.thiengkin.ui.theme.S4
import com.thiengkin.ui.theme.S7

/**
 * Screen 04 — Near-me (Light)
 *
 * v0.4: refactored เป็น LazyColumn เป็น root เพื่อให้ทั้งหน้า scroll พร้อมกัน
 */
@Composable
fun NearMeScreen(
    onRestaurantClick: (String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit,
    onRequestLocationPermission: () -> Unit = {},
    viewModel: NearMeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Auto-request location เหมือน Travel Home
    LaunchedEffect(Unit) {
        try {
            onRequestLocationPermission()
        } catch (_: Throwable) {
            // Ignore: บาง context ไม่พร้อมรับ permission request
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = S4, vertical = S3),
    ) {
        // === item: TopBar ===
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = S2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(text = "ใกล้คุณ", variant = PillVariant.Red)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "ส",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.background,
                    )
                }
            }
        }

        // === item: Title ===
        item {
            Text(
                text = "ร้านแถวนี้",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }

        // === item: Sub-text ===
        item {
            LocationSubtext(
                location = state.location,
                radiusKm = state.radiusKm,
                count = state.restaurants.size,
                onRequestPermission = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) viewModel.requestLocation() else onRequestLocationPermission()
                },
                modifier = Modifier.padding(top = 2.dp, bottom = S2),
            )
        }

        // === item: Search input ===
        item {
            SearchInput(
                leadingIcon = Icons.Filled.Search,
                placeholder = "ค้นหาร้าน...",
                showArrow = false,
                modifier = Modifier.padding(bottom = S2),
            )
        }

        // === item: Radius chips ===
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(S2),
                contentPadding = PaddingValues(bottom = S2),
            ) {
                items(RADIUS_OPTIONS) { km ->
                    FilterChip(
                        text = if (km >= 1.0) "${km.toInt()} กม." else "${(km * 1000).toInt()} ม.",
                        active = state.radiusKm == km,
                        onClick = { viewModel.setRadius(km) },
                    )
                }
            }
        }

        // === item: Category chips ===
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(S2),
                contentPadding = PaddingValues(bottom = S2),
            ) {
                items(CATEGORY_OPTIONS) { cat ->
                    FilterChip(
                        text = cat,
                        active = state.activeCategory == cat,
                        onClick = { viewModel.setCategory(cat) },
                    )
                }
            }
        }

        // === item: Result count row ===
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = S2),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${state.restaurants.size} ร้าน · เรียงตามระยะ",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "กรอง ▾",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // === items: Restaurant list OR empty state ===
        if (state.restaurants.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = S7),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "ไม่มีร้านในรัศมี ${radiusLabel(state.radiusKm)}จากตำแหน่งนี้",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.restaurants, key = { it.id }) { r ->
                RestaurantCard(
                    restaurant = r,
                    etaText = etaTextFor(r.distanceMeters),
                    distText = distTextFor(r.distanceMeters),
                    onNavigate = { onNavigate(r.lat, r.lng, r.name) },
                    onFavoriteToggle = { viewModel.toggleFavorite(r.id) },
                    onClick = { onRestaurantClick(r.id) },
                )
            }
        }
    }
}

/**
 * Sub-text ใต้หัวเรื่อง — แสดงตำแหน่งปัจจุบัน + รัศมี
 */
@Composable
private fun LocationSubtext(
    location: LocationState,
    radiusKm: Double,
    count: Int,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (mainText, subText, showRetryHint) = when (location) {
        is LocationState.Idle -> Triple("แตะเพื่อระบุตำแหน่ง", null, true)
        is LocationState.Loading -> Triple("กำลังระบุตำแหน่ง...", null, false)
        is LocationState.Granted -> {
            val addr = location.address ?: "ตำแหน่งปัจจุบัน"
            if (location.isFallback) {
                Triple(
                    "${addr} (ค่าเริ่มต้น)",
                    location.fallbackReason ?: "แตะเพื่อลอง GPS จริง",
                    true,
                )
            } else {
                Triple("📍 $addr", null, true)
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = showRetryHint) { onRequestPermission() },
    ) {
        Text(
            text = mainText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = "ในรัศมี ${radiusLabel(radiusKm)} · $count ร้าน",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (subText != null) {
            Text(
                text = subText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun radiusLabel(km: Double): String =
    if (km >= 1.0) "${km.toInt()} กม." else "${(km * 1000).toInt()} ม."

private fun distTextFor(meters: Int?): String =
    if (meters != null) {
        if (meters < 1000) "$meters ม." else "%.1f กม.".format(meters / 1000.0)
    } else {
        "—"
    }

private fun etaTextFor(meters: Int?): String {
    if (meters == null) return "—"
    val minutes = (meters / 1000.0 / 60.0 * 60).toInt().coerceAtLeast(1)
    return "ขับ $minutes นาที"
}

private val RADIUS_OPTIONS = listOf(1.0, 3.0, 5.0, 10.0)

private val CATEGORY_OPTIONS = listOf(
    "ทั้งหมด",
    "ก๋วยเตี๋ยว",
    "ข้าวราดแกง",
    "กาแฟ",
    "เปิดดึก",
)
