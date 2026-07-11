package com.thiengkin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.thiengkin.R

/**
 * Typography — Sarabun ตัวเดียว (Display 800 + Body 400)
 * ไม่ pair serif+sans (AI ไม่เคยทำ — นี่คือ design choice)
 *
 * Font ใช้ Downloadable Fonts (Google Fonts) — โหลดครั้งแรกจาก Google Play Services
 * Fallback: ถ้าไม่มี Play Services → bundled font ใน res/font/ (ดู SETUP.md)
 */

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

val SarabunFont = GoogleFont("Sarabun")

val Sarabun = FontFamily(
    Font(googleFont = SarabunFont, fontProvider = provider, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(googleFont = SarabunFont, fontProvider = provider, weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(googleFont = SarabunFont, fontProvider = provider, weight = FontWeight.ExtraBold, style = FontStyle.Normal),
)

/**
 * Type scale — ใช้น้ำหนัก 800 (display) / 700 (heading) / 400 (body) ทำหน้าที่แทน serif contrast
 */
val AppTypography = Typography(
    // Greeting / Hero — single line
    displayLarge = TextStyle(
        fontFamily = Sarabun,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.5).sp,
    ),
    // Page title
    headlineLarge = TextStyle(
        fontFamily = Sarabun,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sarabun,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    ),
    // Card title
    titleLarge = TextStyle(
        fontFamily = Sarabun,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sarabun,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),
    // Body
    bodyLarge = TextStyle(
        fontFamily = Sarabun,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sarabun,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sarabun,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    // Caption / microcopy
    labelLarge = TextStyle(
        fontFamily = Sarabun,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sarabun,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.5.sp,
    ),
)
