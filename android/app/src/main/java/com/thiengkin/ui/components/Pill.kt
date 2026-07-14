package com.thiengkin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.thiengkin.ui.theme.S2
import com.thiengkin.ui.theme.S3
import com.thiengkin.ui.theme.S4

/**
 * Pill — small status badge with optional dot / leading icon
 *
 * Variants:
 * - **Soft (default)**: bg = primaryContainer (FEF2F2) + fg = primary + dot
 *   - เดิมใช้ทั้ง Travel Home, Near Me, Favorites
 * - **Solid** (M2 2026-07-14): bg = primary (DC2626) + fg = onPrimary + leading icon
 *   - ใช้ใน Travel Home (M2 redesign) — LINE MAN style
 *
 * Params:
 * - `leadingIcon`: ImageVector? (M2) — when set, replaces dot in Solid variant
 * - `showDot`: Boolean (default true) — backward-compat, ignored if leadingIcon set
 */
enum class PillVariant { Red, Yellow, Green, Gray, Solid }

@Composable
fun Pill(
    text: String,
    variant: PillVariant = PillVariant.Red,
    showDot: Boolean = true,
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when (variant) {
        PillVariant.Red -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        PillVariant.Yellow -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        PillVariant.Green -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f) to MaterialTheme.colorScheme.tertiary
        PillVariant.Gray -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        // M2: Solid variant — primary bg + onPrimary fg (LINE MAN style)
        PillVariant.Solid -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = S3, vertical = S2 + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading icon (M2) — takes priority over dot when set
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier
                    .size(11.dp)
                    .padding(end = 3.dp),
            )
        } else if (showDot && variant != PillVariant.Solid) {
            // Dot (legacy behavior — only for Soft variants, not Solid)
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(S2 + 2.dp))
        } else if (showDot && variant == PillVariant.Solid) {
            // Solid with showDot=true (legacy caller) — still show dot for back-compat
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(S2 + 2.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
        )
    }
}
