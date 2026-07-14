package com.thiengkin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thiengkin.R
import com.thiengkin.ui.theme.S1
import com.thiengkin.ui.theme.S2
import com.thiengkin.ui.theme.S3

/**
 * CategoryGrid — 5×2 grid ของหมวดอาหาร (M2 2026-07-14)
 *
 * ใช้:
 * - Travel Home: ระหว่าง AdSlot กับ "ใกล้คุณ" section
 * - Near Me: ระหว่าง AdSlot กับ Radius chips
 *
 * Data source: 10 หมวดจาก `assets/category_images/` (0.jpg ... 9.jpg)
 * - 0.jpg = noodle, 1.png = rice, 2.jpg = cafe, 3.png = fastfood, 4.jpg = bakery
 * - 5.jpg = papaya, 6.jpg = salad, 7.jpg = pub, 8.jpeg = dessert, 9.jpg = late
 *
 * Params:
 * - `onItemClick(category)`: optional — tap to filter
 * - `selectedCategory`: optional — highlight selected
 */
data class CategoryItem(
    val key: String,            // internal key for filter ("noodle", "cafe", etc.)
    val label: String,          // Thai label ("ก๋วยเตี๋ยว")
    val imageRes: Int,          // drawable resource (R.drawable.category_xxx)
)

@Composable
fun CategoryGrid(
    items: List<CategoryItem> = DEFAULT_CATEGORIES,
    selectedKey: String? = null,
    onItemClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(168.dp),  // 5 cols × 2 rows × ~80dp/row
        userScrollEnabled = false,
    ) {
        items(items) { item ->
            CategoryItem(
                item = item,
                selected = item.key == selectedKey,
                onClick = onItemClick?.let { { it(item.key) } },
            )
        }
    }
}

@Composable
private fun CategoryItem(
    item: CategoryItem,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = S1)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = item.imageRes),
                contentDescription = item.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * Default 10 categories — ใช้ drawable จาก `res/drawable/category_xxx.{jpg,png}`
 *
 * ไฟล์ถูก copy มาจาก `category_images/` folder แล้ว (M2 2026-07-14)
 * ถ้าเพิ่มหมวดใหม่ → copy รูป + เพิ่ม CategoryItem entry ด้านล่าง
 */
private val DEFAULT_CATEGORIES = listOf(
    CategoryItem("noodle", "ก๋วยเตี๋ยว", R.drawable.category_noodle),
    CategoryItem("rice", "ข้าวราดแกง", R.drawable.category_rice),
    CategoryItem("cafe", "คาเฟ่", R.drawable.category_cafe),
    CategoryItem("fastfood", "ฟาสต์ฟู้ด", R.drawable.category_fastfood),
    CategoryItem("bakery", "เบเกอรี่", R.drawable.category_bakery),
    CategoryItem("papaya", "ส้มตำ", R.drawable.category_papaya),
    CategoryItem("salad", "สลัด", R.drawable.category_salad),
    CategoryItem("pub", "ผับบาร์", R.drawable.category_pub),
    CategoryItem("dessert", "ของหวาน", R.drawable.category_dessert),
    CategoryItem("late", "เปิดดึก", R.drawable.category_late),
)
