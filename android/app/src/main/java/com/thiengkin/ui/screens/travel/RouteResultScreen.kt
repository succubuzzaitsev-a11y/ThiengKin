package com.thiengkin.ui.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thiengkin.data.LocationState
import com.thiengkin.data.distanceMeters
import com.thiengkin.ui.components.FilterChip
import com.thiengkin.ui.components.Pill
import com.thiengkin.ui.components.PillVariant
import com.thiengkin.ui.components.RestaurantCard
import com.thiengkin.ui.components.RouteLine
import com.thiengkin.ui.components.RouteStop
import com.thiengkin.ui.theme.S2
import com.thiengkin.ui.theme.S3
import com.thiengkin.ui.theme.S4

/**
 * Screen 02 — Route Result (Dark)
 *
 * แสดง "จุดแวะ" 5 อันดับแรกจาก manual picks (35 ร้านที่คัดสรร)
 * + filter chips (แนะนำ / คนท้องถิ่น / เปิดอยู่ / ใกล้ที่สุด)
 * + RouteLine แสดง stops จริงจาก result (แทน mock กรุงเทพ→เชียงใหม่)
 *
 * Phase 1: ไม่มี route A→B จริง — ใช้ manual picks เป็น "จุดแวะแนะนำ" แทน
 * Phase 1.5: เพิ่ม Directions API + corridor cache
 */
@Composable
fun RouteResultScreen(
    onRestaurantClick: (String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit,
    onBack: () -> Unit,
    viewModel: RouteResultViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = S4),
    ) {
        // Top: pill + back
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = S3, bottom = S2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(text = "บนเส้นทาง", variant = PillVariant.Yellow)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "กลับ",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Header — dynamic count
        Text(
            text = "${state.restaurants.size} จุดแวะแนะนำ",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )

        // Subtitle — dynamic district
        Text(
            text = routeSubtitle(state.location, state.restaurants),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )

        // RouteLine — แสดง stops จริง (ร้านอันดับ 1-5) แทน hardcoded route
        if (state.restaurants.isNotEmpty()) {
            val top = state.restaurants.first()
            RouteLine(
                from = "📍 คุณ",
                to = top.district?.take(8) ?: "เชียงใหม่",
                distText = routeDistText(state.location, top),
                stops = state.restaurants.mapIndexed { idx, r ->
                    RouteStop(
                        name = (r.nameTh ?: r.name).take(10),
                        isActive = idx == 0,  // จุดเริ่มต้น
                    )
                },
                modifier = Modifier.padding(top = S3),
            )
        }

        // Filter chips — wire ใช้งานจริง
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(S2),
            contentPadding = PaddingValues(vertical = S2),
        ) {
            items(RouteFilter.values()) { filter ->
                FilterChip(
                    text = filter.label,
                    active = state.activeFilter == filter,
                    onClick = { viewModel.setFilter(filter) },
                )
            }
        }

        // Restaurant list
        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (state.restaurants.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "ไม่มีร้านใน filter นี้",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(S2)) {
                items(state.restaurants, key = { it.id }) { r ->
                    RestaurantCard(
                        restaurant = r,
                        etaText = etaTextFor(r.distanceMeters),
                        distText = distTextFor(r.distanceMeters),
                        detourText = detourTextFor(r.distanceMeters),
                        onNavigate = { onNavigate(r.lat, r.lng, r.name) },
                        onFavoriteToggle = { viewModel.toggleFavorite(r.id) },
                        onClick = { onRestaurantClick(r.id) },
                    )
                }
                item { Spacer(Modifier.height(S4)) }
            }
        }
    }
}

/**
 * Subtitle — แสดง location + count
 * - Real GPS: "📍 {address} · 5 ร้านคัดสรร"
 * - Fallback: "เชียงใหม่ (ค่าเริ่มต้น) · 5 ร้านคัดสรร"
 */
private fun routeSubtitle(location: LocationState, restaurants: List<com.thiengkin.data.Restaurant>): String {
    val locLabel = when (location) {
        is LocationState.Granted -> location.address
            ?: if (location.isFallback) "เชียงใหม่ (ค่าเริ่มต้น)" else "ตำแหน่งปัจจุบัน"
        else -> "กำลังระบุตำแหน่ง..."
    }
    val count = restaurants.size
    return "$locLabel · $count ร้านคัดสรร"
}

private fun routeDistText(location: LocationState, first: com.thiengkin.data.Restaurant): String {
    if (location !is LocationState.Granted || location.isFallback) return "manual picks"
    val meters = first.distanceMeters ?: return "manual picks"
    val km = meters / 1000.0
    return "%.1f กม.".format(km)
}

private fun etaTextFor(meters: Int?): String =
    if (meters != null) {
        val min = (meters / 1000.0 / 60.0 * 60).toInt().coerceAtLeast(1)
        "อีก $min นาที"
    } else {
        "—"
    }

private fun distTextFor(meters: Int?): String =
    if (meters != null) {
        if (meters < 1000) "$meters ม." else "%.1f กม.".format(meters / 1000.0)
    } else {
        "—"
    }

/** Detour แบบหยาบ — ถ้า < 1 km → +X นาที เดิน, 1-5 km → +X นาที ขับ, 5+ km → +X นาที ขับ */
private fun detourTextFor(meters: Int?): String? {
    if (meters == null) return null
    // ~30 km/h (highway) → detour minutes
    val detourMin = (meters / 1000.0 / 30.0 * 60).toInt().coerceAtLeast(1)
    return "+$detourMin นาที"
}
