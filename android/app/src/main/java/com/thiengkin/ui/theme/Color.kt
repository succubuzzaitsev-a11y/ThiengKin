package com.thiengkin.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens v3.0 — ทางตรง (Direct Path)
 *
 * 4 สี + semantic เสริม:
 * - ink    = primary text, dark surfaces
 * - paper  = light surfaces
 * - red    = brand (Thieng Tham logo) + action CTA
 * - mustard = rating, active state, ETA
 * - green  = "เปิดอยู่" (semantic, universal)
 */

// === 4 brand colors ===
val Ink = Color(0xFF0F0F0F)
val Paper = Color(0xFFFAFAFA)
val Red = Color(0xFFDC2626)
val Mustard = Color(0xFFFACC15)

// === Semantic ===
val Green = Color(0xFF16A34A)  // "เปิดอยู่"
val RedPress = Color(0xFFB91C1C)
val MustardPress = Color(0xFFB88A18)

// === Neutrals (4 shades พอ — ไม่ต้อง scale 9) ===
val Ink2 = Color(0xFF5A5A5A)
val Ink3 = Color(0xFF9A9A9A)
val Line = Color(0xFFE5E5E5)
val Paper2 = Color(0xFFF2F2F2)

// === App dark mode (Travel) ===
val AppBg = Color(0xFF0F0F0F)
val AppCard = Color(0xFF1A1A1A)
val AppCard2 = Color(0xFF242424)
val AppLine = Color(0xFF2A2A2A)
val AppInk = Color(0xFFFAFAFA)
val AppInk2 = Color(0x99FAFAFA)   // 60%
val AppInk3 = Color(0x4DFAFAFA)   // 30%
val AppRed = Color(0xFFEF4444)     // brightened for dark BG
val AppMustard = Color(0xFFFACC15)
val AppGreen = Color(0xFF4ADE80)   // brightened
