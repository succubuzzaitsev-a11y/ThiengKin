package com.thiengkin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thiengkin.ui.theme.S1

/**
 * TopBarAvatar — circular avatar ที่ top bar (Travel Home)
 *
 * Variants:
 * - **Solid** (M2 2026-07-14): gradient red (135deg, DC2626 → F87171) — 32dp
 *   - ใช้ใน Travel Home M2 redesign — ดู "premium" ขึ้น
 * - **Plain** (legacy): onSurfaceVariant (gray) — 34dp
 *   - ยังใช้ใน Near Me + Favorites (KEEP current)
 *
 * Params:
 * - `initials`: ตัวอักษรแสดงใน avatar (default "ส")
 * - `size`: dp (default 32dp)
 * - `onClick`: optional — tap to open profile
 */
@Composable
fun TopBarAvatar(
    initials: String = "ส",
    size: androidx.compose.ui.unit.Dp = 32.dp,
    variant: TopBarAvatarVariant = TopBarAvatarVariant.Solid,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val (brush, color) = when (variant) {
        TopBarAvatarVariant.Solid -> Brush.linearGradient(
            colors = listOf(
                Color(0xFFDC2626),  // red-600
                Color(0xFFF87171),  // red-400
            )
        ) to Color.White
        TopBarAvatarVariant.Plain -> Brush.linearGradient(
            colors = listOf(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        ) to MaterialTheme.colorScheme.background
    }

    val baseModifier = modifier
        .size(size)
        .clip(CircleShape)
        .background(brush)

    val clickableModifier = if (onClick != null) baseModifier.clickable(onClick = onClick) else baseModifier

    Box(
        modifier = clickableModifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

enum class TopBarAvatarVariant { Solid, Plain }
