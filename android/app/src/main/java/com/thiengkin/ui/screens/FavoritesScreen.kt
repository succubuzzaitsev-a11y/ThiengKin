package com.thiengkin.ui.screens

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
import com.thiengkin.ui.components.Pill
import com.thiengkin.ui.components.PillVariant
import com.thiengkin.ui.components.RestaurantCard
import com.thiengkin.ui.theme.S2
import com.thiengkin.ui.theme.S3
import com.thiengkin.ui.theme.S4

/**
 * Screen 07 — Favorites (Light)
 *
 * ร้านที่บันทึกไว้ · 3 sort tabs (ล่าสุด / ตามจังหวัด / ตาม rating)
 *
 * v0.2: Sort tabs ทำงานจริง · subtitle เปลี่ยนตาม mode · ใช้ district แทน favoritedDaysAgo (null)
 */
@Composable
fun FavoritesScreen(
    onRestaurantClick: (String) -> Unit,
    onNavigate: (Double, Double, String) -> Unit,
    viewModel: FavoritesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = S4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = S3, bottom = S2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(text = "รายการโปรด", variant = PillVariant.Yellow)
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("ส", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.background)
            }
        }

        Text(
            "ร้านที่บันทึกไว้",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )

        // Subtitle — dynamic ตาม sortMode
        Text(
            text = favoritesSubtitle(state.sortMode, state.restaurants.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )

        // Sort tabs — wire ใช้งานจริง
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(vertical = S3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(FavoritesSort.values()) { mode ->
                SortTab(
                    label = mode.label,
                    count = if (mode == state.sortMode) state.restaurants.size else 0,
                    active = mode == state.sortMode,
                    onClick = { viewModel.setSortMode(mode) },
                )
            }
        }

        // List หรือ empty state
        if (state.restaurants.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = S4),
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(S2)) {
                items(state.restaurants, key = { it.id }) { r ->
                    RestaurantCard(
                        restaurant = r,
                        etaText = districtLabel(r.district),
                        distText = r.category ?: "—",
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
 * Subtitle — เปลี่ยนตาม sort mode
 * - ล่าสุด: "N ร้าน · เรียงตามชื่อ"
 * - ตามจังหวัด: "N ร้าน · เรียงตามอำเภอ"
 * - ตาม rating: "N ร้าน · เรียงตาม rating"
 */
private fun favoritesSubtitle(mode: FavoritesSort, count: Int): String {
    val sortDesc = when (mode) {
        FavoritesSort.Latest -> "เรียงตามชื่อ"
        FavoritesSort.Province -> "เรียงตามอำเภอ"
        FavoritesSort.Rating -> "เรียงตาม rating"
    }
    return "$count ร้าน · $sortDesc"
}

/** แปลง district enum เป็น label สั้นๆ */
private fun districtLabel(district: String?): String =
    when {
        district == null -> "—"
        district.contains("Mueang Chiang Mai", ignoreCase = true) -> "📍 เมืองเชียงใหม่"
        district.contains("Mae Rim", ignoreCase = true) -> "📍 แม่ริม"
        district.contains("Hang Dong", ignoreCase = true) -> "📍 หางดง"
        district.contains("Lamphun", ignoreCase = true) -> "📍 ลำพูน"
        else -> "📍 $district"
    }

@Composable
private fun SortTab(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = S3, vertical = S2 + 2.dp)
            .background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (count > 0) {
            Text(
                text = " $count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
