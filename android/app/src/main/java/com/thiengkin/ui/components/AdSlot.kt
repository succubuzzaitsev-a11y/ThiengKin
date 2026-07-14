package com.thiengkin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thiengkin.ui.theme.S1
import com.thiengkin.ui.theme.S2
import com.thiengkin.ui.theme.S3

/**
 * AdSlot — 140dp placeholder banner
 *
 * ใช้แสดง "พื้นที่โฆษณา" ในหน้า Near Me (เหนือ radius chips)
 * Dashed border + "โฆษณา" corner label — profit path สำหรับ sponsor
 *
 * M2 (2026-07-14): NEW component
 *
 * Variants:
 * - Default: 140dp height, centered content (icon + title + subtitle)
 * - onClick: ถ้าใส่จะกดได้ (กรณีต้องการ route ไป sponsor detail)
 */
@Composable
fun AdSlot(
    title: String,
    subtitle: String? = null,
    iconText: String = "📢",
    height: Dp = 110.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .let { if (onClick != null) it.padding(0.dp).also { _ -> /* clickable via parent */ } else it }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // "โฆษณา" corner label
        Text(
            text = "โฆษณา",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        // Centered content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                text = iconText,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(S1))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
