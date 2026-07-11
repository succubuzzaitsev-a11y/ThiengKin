package com.thiengkin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thiengkin.ui.theme.AppInk2
import com.thiengkin.ui.theme.AppRed
import com.thiengkin.ui.theme.S2
import com.thiengkin.ui.theme.S3
import com.thiengkin.ui.theme.S4

/**
 * Pill — small status badge with optional dot
 *
 * Variants:
 * - red (default) — "กำลังขับรถ", "ใกล้คุณ"
 * - yellow — "คนท้องถิ่นแนะนำ", "บนเส้นทาง"
 * - green — "เปิดอยู่"
 * - gray — "กำลังโหลด", "Offline"
 */
enum class PillVariant { Red, Yellow, Green, Gray }

@Composable
fun Pill(
    text: String,
    variant: PillVariant = PillVariant.Red,
    showDot: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when (variant) {
        PillVariant.Red -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        PillVariant.Yellow -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.secondary
        PillVariant.Green -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f) to MaterialTheme.colorScheme.tertiary
        PillVariant.Gray -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = S3, vertical = S2 + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDot) {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(0.dp))
            androidx.compose.foundation.layout.Box(
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
