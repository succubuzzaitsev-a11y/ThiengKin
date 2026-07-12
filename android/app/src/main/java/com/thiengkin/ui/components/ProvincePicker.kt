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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thiengkin.data.District
import com.thiengkin.data.Province
import com.thiengkin.ui.theme.S2
import com.thiengkin.ui.theme.S3
import com.thiengkin.ui.theme.S4
import com.thiengkin.ui.theme.S5
import com.thiengkin.ui.theme.S7

/**
 * ProvincePicker — pill แสดงจังหวัด + อำเภอที่เลือก + tap เปิด BottomSheet เลือก
 *
 * ใช้แทน CitySelector ในระบบ nationwide (M1.b+)
 *
 * Layout:
 *  - trigger pill:
 *      [📍 กรุงเทพมหานคร / พระนคร ▾]   (district selected)
 *      [📍 กรุงเทพมหานคร ▾]            (only province)
 *      [📍 เลือกจังหวัด ▾]            (none)
 *
 *  - tap → ModalBottomSheet 2 steps:
 *      1. Searchable province list (77 จังหวัด)
 *      2. District list within selected province (with "ทั้งจังหวัด" option)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvincePicker(
    selectedProvince: Province?,
    selectedDistrict: District?,
    provinces: List<Province>,
    districtsForSelectedProvince: List<District>,
    onProvinceSelected: (Province) -> Unit,
    onDistrictSelected: (District?) -> Unit,  // null = ทั้งจังหวัด
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }

    // Picker state — when null = showing province list, when set = showing district list of that province
    var viewingDistrictsFor by remember { mutableStateOf<Province?>(null) }
    var query by remember { mutableStateOf("") }

    // Reset query + drill-down when sheet closes
    LaunchedEffect(showSheet) {
        if (!showSheet) {
            query = ""
            viewingDistrictsFor = null
        }
    }

    val displayText = when {
        selectedDistrict != null -> "${selectedProvince?.nameTh ?: ""} / ${selectedDistrict.nameTh}"
        selectedProvince != null -> selectedProvince.nameTh
        else -> "เลือกจังหวัด"
    }

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
                text = "📍",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
            val viewing = viewingDistrictsFor
            if (viewing == null) {
                ProvinceListContent(
                    provinces = provinces,
                    selectedProvince = selectedProvince,
                    query = query,
                    onQueryChange = { query = it },
                    onProvinceClick = { province ->
                        onProvinceSelected(province)
                        onDistrictSelected(null)  // reset district
                        viewingDistrictsFor = province
                        query = ""
                    },
                )
            } else {
                DistrictListContent(
                    province = viewing,
                    districts = districtsForSelectedProvince.filter { it.provinceId == viewing.id },
                    selectedDistrict = selectedDistrict,
                    onBack = {
                        viewingDistrictsFor = null
                        query = ""
                    },
                    onDistrictClick = { districtOrNull ->
                        onDistrictSelected(districtOrNull)
                        showSheet = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ProvinceListContent(
    provinces: List<Province>,
    selectedProvince: Province?,
    query: String,
    onQueryChange: (String) -> Unit,
    onProvinceClick: (Province) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = S4, vertical = S2),
    ) {
        Text(
            text = "เลือกจังหวัด",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = S3),
        )

        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(bottom = S3),
        )

        val filtered = remember(provinces, query) {
            if (query.isBlank()) provinces
            else provinces.filter { p ->
                p.nameTh.contains(query, ignoreCase = true) ||
                    p.nameEn.contains(query, ignoreCase = true)
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = S7),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "ไม่พบจังหวัด \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
            ) {
                items(filtered, key = { it.id }) { province ->
                    ProvinceRow(
                        province = province,
                        selected = province.id == selectedProvince?.id,
                        onClick = { onProvinceClick(province) },
                    )
                }
            }
        }

        Spacer(Modifier.height(S4))
    }
}

@Composable
private fun DistrictListContent(
    province: Province,
    districts: List<District>,
    selectedDistrict: District?,
    onBack: () -> Unit,
    onDistrictClick: (District?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = S4, vertical = S2),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = S3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(S2),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "ย้อนกลับ",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "เลือกอำเภอ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = province.nameTh,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // "ทั้งจังหวัด" — drill-up option (district = null)
        DistrictRow(
            title = "ทั้งจังหวัด",
            subtitle = "ดูร้านอาหารทุกอำเภอใน${province.nameTh}",
            selected = selectedDistrict == null,
            onClick = { onDistrictClick(null) },
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = S2),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        )

        if (districts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = S5),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "ไม่มีข้อมูลอำเภอ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items(districts, key = { it.id }) { district ->
                    DistrictRow(
                        title = district.nameTh,
                        subtitle = district.nameEn,
                        selected = district.id == selectedDistrict?.id,
                        onClick = { onDistrictClick(district) },
                    )
                }
            }
        }

        Spacer(Modifier.height(S4))
    }
}

@Composable
private fun ProvinceRow(
    province: Province,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = province.nameTh,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = province.nameEn,
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
        } else {
            Text(
                text = "▸",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DistrictRow(
    title: String,
    subtitle: String?,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = S3, vertical = S2 + 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = S2),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "ค้นหาจังหวัด (ไทย/อังกฤษ)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
