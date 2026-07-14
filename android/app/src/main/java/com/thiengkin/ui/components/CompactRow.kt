package com.thiengkin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thiengkin.data.Restaurant
import com.thiengkin.ui.theme.S1
import com.thiengkin.ui.theme.S2

/**
 * CompactRow — clean compact list row (LINE MAN-style)
 *
 * Layout:
 *  [76×76 photo] | ETA tag (green/red/gray) + name
 *                | ★ rating · reviews · เปิดอยู่
 *                | [นำทาง button] [♡ fav button]
 *
 * M2 (2026-07-14): NEW component — replaces RestaurantCard ใน Near Me
 *
 * ใช้:
 * - Near Me: list ร้านอาหาร (76×76 photo + ETA tag ตาม status + นำทาง + ♡)
 * - ขนาด: 76×76 photo (vs 88×88 เดิม — compact ขึ้น)
 * - ETA tag colors:
 *   - green = เปิดอยู่ (มี opening hours ที่ยังเปิด)
 *   - red = ปิดแล้ว
 *   - gray = ระยะทาง
 */
@Composable
fun CompactRow(
    restaurant: Restaurant,
    distanceMeters: Int? = null,
    onNavigate: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Photo placeholder (76x76)
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "FOOD",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Body
        Column(
            modifier = Modifier
                .padding(start = S2)
                .fillMaxWidth(),
        ) {
            // Row: ETA tag
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EtaTag(
                    status = etaStatus(restaurant),
                    text = etaText(restaurant, distanceMeters),
                )
                if (distanceText(distanceMeters).isNotEmpty()) {
                    EtaTag(
                        status = EtaStatus.Distance,
                        text = distanceText(distanceMeters),
                    )
                }
            }

            // Name
            Text(
                text = restaurant.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = S1),
            )

            // Meta: rating · reviews · open
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                if (restaurant.rating != null) {
                    Text(
                        text = "★ ${"%.1f".format(restaurant.rating)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,  // mustard
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (restaurant.rating != null && restaurant.reviewCount != null) {
                    Box(
                        modifier = Modifier
                            .size(2.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.outline),
                    )
                }
                if (restaurant.reviewCount != null) {
                    Text(
                        text = "${restaurant.reviewCount} รีวิว",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(2.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.outline),
                )
                Text(
                    text = if (isOpen(restaurant)) "เปิดอยู่" else "ปิดแล้ว",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isOpen(restaurant)) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Actions: นำทาง + ♡
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = S2),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Primary: นำทาง
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onNavigate),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "นำทาง",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                // Favorite ♡
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (restaurant.isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(onClick = onFavoriteToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (restaurant.isFavorite) Icons.Filled.Favorite
                        else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (restaurant.isFavorite) "ลบออกจากรายการโปรด"
                        else "เพิ่มในรายการโปรด",
                        tint = if (restaurant.isFavorite) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * ETA tag — small badge with status color
 *
 * Status → color:
 *  - Open   → green (เปิดอยู่)
 *  - Closed → red   (ปิดแล้ว)
 *  - Distance → gray (ระยะทาง)
 */
@Composable
private fun EtaTag(status: EtaStatus, text: String) {
    val (bg, fg) = when (status) {
        EtaStatus.Open -> MaterialTheme.colorScheme.tertiary to Color.White
        EtaStatus.Closed -> MaterialTheme.colorScheme.error to Color.White
        EtaStatus.Distance -> MaterialTheme.colorScheme.onSurfaceVariant to Color.White
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
        )
    }
}

private enum class EtaStatus { Open, Closed, Distance }

/**
 * เช็คว่าร้านเปิดอยู่ไหม — ใช้ opening_hours string ถ้ามี (ยังไม่ implement full parser ใน M2)
 * fallback: ถ้าไม่มี openingHours → ถือว่าเปิด
 */
private fun isOpen(restaurant: Restaurant): Boolean {
    // TODO M2.1: parse opening_hours string + check current time
    // ตอนนี้: ถ้ามี openingHours → ถือว่าเปิด (จะ refine ใน next step)
    return !restaurant.openingHours.isNullOrEmpty() || restaurant.openingHours == null
}

/**
 * ETA text — "ขับ X นาที" หรือ "ปิดแล้ว"
 */
private fun etaText(restaurant: Restaurant, distanceMeters: Int?): String {
    return when {
        !isOpen(restaurant) -> "ปิดแล้ว"
        distanceMeters == null -> "—"
        distanceMeters < 100 -> "< 100 ม."
        distanceMeters < 1000 -> "$distanceMeters ม."
        else -> {
            val minutes = (distanceMeters / 1000.0 * 60 / 60).toInt().coerceAtLeast(1)
            "ขับ $minutes นาที"
        }
    }
}

private fun distanceText(distanceMeters: Int?): String {
    if (distanceMeters == null) return ""
    return if (distanceMeters < 1000) "$distanceMeters ม."
    else "%.1f กม.".format(distanceMeters / 1000.0)
}

private fun etaStatus(restaurant: Restaurant): EtaStatus {
    return if (isOpen(restaurant)) EtaStatus.Open else EtaStatus.Closed
}
