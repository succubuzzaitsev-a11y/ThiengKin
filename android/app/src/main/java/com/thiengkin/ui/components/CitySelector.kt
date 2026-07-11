package com.thiengkin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.thiengkin.data.Cities
import com.thiengkin.data.City
import com.thiengkin.ui.theme.S2
import com.thiengkin.ui.theme.S3
import com.thiengkin.ui.theme.S4

/**
 * CitySelector — pill แสดงเมืองที่เลือก + tap เปิด BottomSheet เลือกเมือง
 *
 * ใช้แทน hardcode "เชียงใหม์" — user เลือกเมืองได้
 *
 * Layout:
 *  [📍 กรุงเทพมหานคร ▾]  ← pill (clickable)
 *
 *  → tap → ModalBottomSheet:
 *  ┌─────────────────────────┐
 *  │ เลือกเมือง               │
 *  │ 🏙️  กรุงเทพมหานคร    ✓  │
 *  │ 🏔️  เชียงใหม่           │
 *  │ ⛰️  เชียงราย           │
 *  │ ...                     │
 *  └─────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitySelector(
    selected: City?,
    onCitySelected: (City) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }

    // Pill
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { showSheet = true }
            .padding(horizontal = S3 + 2.dp, vertical = S2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = selected?.emoji ?: "📍",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = selected?.nameTh ?: "เลือกเมือง",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "▾",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = S4, vertical = S2),
            ) {
                Text(
                    text = "เลือกเมือง",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = S3),
                )

                Cities.all.forEach { city ->
                    CityRow(
                        city = city,
                        selected = city.id == selected?.id,
                        onClick = {
                            onCitySelected(city)
                            showSheet = false
                        },
                    )
                }

                Spacer(Modifier.height(S4))
            }
        }
    }
}

@Composable
private fun CityRow(
    city: City,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = S3, vertical = S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = city.emoji,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.size(S3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = city.nameTh,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = city.nameEn,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
