package com.thiengkin.ui.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thiengkin.ui.components.Pill
import com.thiengkin.ui.components.PillVariant
import com.thiengkin.ui.theme.S1
import com.thiengkin.ui.theme.S2
import com.thiengkin.ui.theme.S3
import com.thiengkin.ui.theme.S4

/**
 * Screen 03 — Restaurant Detail (Dark)
 *
 * Layout: hero img · name · meta · สรุปรีวิว (Ai) · เมนูเด่น · CTA นำทาง
 *
 * v0.2: dynamic data (aiSummary / menuText) + proper back button + real price tier
 */
@Composable
fun RestaurantDetailScreen(
    restaurantId: String,
    onBack: () -> Unit,
    onNavigate: (Double, Double, String) -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    viewModel: RestaurantDetailViewModel = viewModel(),
) {
    // Load once per id — ไม่ใช่ทุก recompose
    LaunchedEffect(restaurantId) {
        viewModel.load(restaurantId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = S4),
    ) {
        // Top: pill + back arrow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = S3, bottom = S2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(text = "คนท้องถิ่นแนะนำ", variant = PillVariant.Yellow)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Favorite toggle
                if (onToggleFavorite != null) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onToggleFavorite() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.restaurant?.isFavorite == true) Icons.Filled.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (state.restaurant?.isFavorite == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.size(S2))
                }
                // Back arrow
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
        }

        // Hero img placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "FOOD PHOTO",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.restaurant?.let { r ->
            // Name
            Text(
                text = r.name,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = S3),
            )

            // Address
            if (!r.address.isNullOrBlank()) {
                Text(
                    text = "📍 ${r.address}${r.district?.let { " · $it" }.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Meta: rating · reviews · price · open
            Row(
                modifier = Modifier.padding(top = S2, bottom = S3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(S2),
            ) {
                if (r.rating != null) {
                    Text(
                        text = "★ ${"%.1f".format(r.rating)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (r.reviewCount != null) {
                    Dot()
                    Text(
                        text = "${r.reviewCount} รีวิว",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (r.price != null) {
                    Dot()
                    Text(
                        text = priceTier(r.price),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Dot()
                Text(
                    text = "เปิดอยู่",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            // AI Summary card — ใช้ r.aiSummary ถ้ามี, ไม่งั้นโชว์ placeholder
            SummaryCard(
                aiSummary = r.aiSummary,
                category = r.category,
            )

            // เมนูเด่น — ใช้ r.menuText (แต่ละบรรทัด = 1 เมนู) ถ้ามี
            MenuSection(menuText = r.menuText)

            // ติดต่อ
            ContactSection(tel = r.tel, website = r.website)

            Spacer(modifier = Modifier.height(S4))

            // CTA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onNavigate(r.lat, r.lng, r.name) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Navigation, null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(S2))
                Text(
                    "นำทางไปร้านนี้",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(modifier = Modifier.height(S4))
        }
    }
}

@Composable
private fun Dot() {
    Box(
        modifier = Modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outline),
    )
}

@Composable
private fun SummaryCard(aiSummary: String?, category: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(S3 + 2.dp),
    ) {
        Text(
            "สรุปรีวิว (Ai)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = S1 + 2.dp),
        )
        if (!aiSummary.isNullOrBlank()) {
            Text(
                aiSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = category?.let { "หมวด$it · ยังไม่มีสรุปรีวิว — ดูรีวิวต้นฉบับได้ที่ Google Maps" }
                    ?: "ยังไม่มีสรุปรีวิว",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MenuSection(menuText: String?) {
    val items = menuText
        ?.split('\n', '·', '|', ',')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.take(5)
        .orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = S4, bottom = S2),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "เมนูเด่น",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (items.isNotEmpty()) {
            Text(
                "${items.size} เมนู",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (items.isEmpty()) {
        Text(
            "ยังไม่มีข้อมูลเมนู",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = S2),
        )
    } else {
        items.forEach { name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = S2),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "ดูเมนู",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ContactSection(tel: String?, website: String?) {
    if (tel.isNullOrBlank() && website.isNullOrBlank()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = S3, bottom = S2),
        horizontalArrangement = Arrangement.spacedBy(S2),
    ) {
        if (!tel.isNullOrBlank()) {
            ContactChip(icon = Icons.Filled.Phone, text = tel)
        }
        if (!website.isNullOrBlank()) {
            ContactChip(icon = Icons.Filled.Navigation, text = "เว็บไซต์")
        }
    }
}

@Composable
private fun ContactChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = S3, vertical = S2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Map Foursquare price tier (1-4) → "$ / $$ / $$$ / $$$$" */
private fun priceTier(price: Int): String = when (price.coerceIn(1, 4)) {
    1 -> "$"
    2 -> "$$"
    3 -> "$$$"
    else -> "$$$$"
}
