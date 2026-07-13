package com.thiengkin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.thiengkin.ui.theme.S2
import com.thiengkin.ui.theme.S3
import com.thiengkin.ui.theme.S4

/**
 * SearchInput — fixed input field with icon + optional arrow / clear button
 *
 * ใช้ใน:
 * - Travel Home (editable — search restaurants by name)
 * - Near-me (editable — same)
 * - Travel route destination (static clickable — เลือกปลายทาง)
 *
 * โหมด:
 * - **Static** (value=null): แสดง placeholder เป็น Text ใน Row + clickable เท่านั้น
 * - **Editable** (value=String): แสดง BasicTextField + cursor + clear button (×) เมื่อมี text
 *   + onValueChange callback + onSearch callback (เมื่อกด Enter/Done)
 */
@Composable
fun SearchInput(
    leadingIcon: ImageVector? = null,
    placeholder: String,
    value: String? = null,
    showArrow: Boolean = false,
    onValueChange: ((String) -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isEditable = value != null
    val keyboard = LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .let {
                if (!isEditable) it.clickable(onClick = onClick) else it
            }
            .padding(horizontal = S4, vertical = S3 + 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = S3),
                )
            }
            if (isEditable) {
                EditableSearchField(
                    value = value!!,
                    placeholder = placeholder,
                    onValueChange = onValueChange ?: {},
                    onSearch = {
                        keyboard?.hide()
                        onSearch?.invoke()
                    },
                )
            } else {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (isEditable && value!!.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange?.invoke("") },
                    modifier = Modifier
                        .padding(start = S2)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "ล้างคำค้น",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(S2),
                    )
                }
            } else if (showArrow) {
                Text(
                    text = "→",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Editable text field — ใช้ภายใน SearchInput ตอน isEditable=true
 *
 * ไม่ได้ใช้ Material3 TextField เพราะอยากให้ visual style เหมือน static mode เดิม
 * (พื้นหลังเดียวกัน, ไม่มี border แยก)
 */
@Composable
private fun EditableSearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            inner()
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
