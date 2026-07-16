package com.thiengkin.data

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * OSM opening_hours format parser.
 *
 * Format: "Mo-Fr 09:00-18:00; Sa 10:00-14:00; Su off"
 *  - Days: Mo, Tu, We, Th, Fr, Sa, Su (English 2-letter)
 *  - Time: HH:MM (24h)
 *  - Multi-segment separated by `;`
 *  - Special: "24/7" → always open | "off" → always closed
 *  - Day range: "Mo-Fr" (Monday to Friday)
 *  - Comma-separated days: "Mo,We,Fr"
 *  - Time range wrap: "22:00-02:00" (open past midnight)
 *
 * Public API:
 *  - [isOpenNow]      — true if open at given moment
 *  - [nextOpenTime]   — "HH:MM" / "พรุ่งนี้ HH:MM" / null
 *  - [formatForDisplay] — "10:00-22:00 (จ-ศ)" / "24 ชม." / null
 *
 * M7 — replaces [com.thiengkin.ui.components.isOpen] stub in CompactRow.kt.
 *
 * Reference: https://wiki.openstreetmap.org/wiki/Key:opening_hours
 */
object OpeningHoursParser {

    private val DAY_MAP = mapOf(
        "Mo" to DayOfWeek.MONDAY,
        "Tu" to DayOfWeek.TUESDAY,
        "We" to DayOfWeek.WEDNESDAY,
        "Th" to DayOfWeek.THURSDAY,
        "Fr" to DayOfWeek.FRIDAY,
        "Sa" to DayOfWeek.SATURDAY,
        "Su" to DayOfWeek.SUNDAY,
    )

    /** Thai 1-letter day names (จ=จันทร์, อ=อังคาร, etc.) */
    private val THAI_DAY_SHORT = mapOf(
        DayOfWeek.MONDAY to "จ",
        DayOfWeek.TUESDAY to "อ",
        DayOfWeek.WEDNESDAY to "พ",
        DayOfWeek.THURSDAY to "พฤ",
        DayOfWeek.FRIDAY to "ศ",
        DayOfWeek.SATURDAY to "ส",
        DayOfWeek.SUNDAY to "อา",
    )

    /**
     * Check if restaurant is open at given time.
     * Returns true if openingHours is null/empty (no info, assume open).
     * "24/7" always returns true.
     */
    fun isOpenNow(openingHours: String?, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val segments = parse(openingHours) ?: return true
        if (segments.isEmpty()) return true  // "off" everywhere or empty after parse

        val today = now.dayOfWeek
        val currentMinutes = now.hour * 60 + now.minute

        for (segment in segments) {
            if (today !in segment.days) continue
            // Normal range: open <= close
            if (segment.openMinutes <= segment.closeMinutes) {
                if (currentMinutes in segment.openMinutes..segment.closeMinutes) return true
            } else {
                // Wrap-around: e.g. 22:00-02:00 = open from 22:00 today to 02:00 tomorrow
                if (currentMinutes >= segment.openMinutes || currentMinutes <= segment.closeMinutes) return true
            }
        }
        return false
    }

    /**
     * Get next opening time as "HH:MM" (today) or "พรุ่งนี้ HH:MM" (tomorrow).
     * Returns null if always open ("24/7") or no more openings found in 24h.
     */
    fun nextOpenTime(openingHours: String?, now: LocalDateTime = LocalDateTime.now()): String? {
        val segments = parse(openingHours) ?: return null
        if (segments.isEmpty()) return null

        val today = now.dayOfWeek
        val currentMinutes = now.hour * 60 + now.minute

        // Check today's remaining segments
        val todayNext = segments
            .filter { today in it.days && it.openMinutes > currentMinutes }
            .minByOrNull { it.openMinutes }
        if (todayNext != null) return formatTime(todayNext.openMinutes)

        // Check tomorrow's first segment
        val tomorrow = today.plus(1)
        val tomorrowNext = segments
            .filter { tomorrow in it.days }
            .minByOrNull { it.openMinutes }
        if (tomorrowNext != null) return "พรุ่งนี้ ${formatTime(tomorrowNext.openMinutes)}"

        return null
    }

    /**
     * Format opening hours for compact display.
     * - null/empty → null
     * - "24/7" → "24 ชม."
     * - Uniform hours (all 7 days same) → "10:00-22:00 (ทุกวัน)"
     * - Range (e.g. Mon-Fri same) → "10:00-22:00 (จ-ศ)"
     * - Multiple distinct time groups → first group with day range
     */
    fun formatForDisplay(openingHours: String?): String? {
        val segments = parse(openingHours) ?: return null
        if (segments.isEmpty()) return null

        // Group by time range
        val byTimeKey = segments.groupBy { "${formatTime(it.openMinutes)}-${formatTime(it.closeMinutes)}" }
        val (timeKey, dayGroups) = byTimeKey.maxByOrNull { it.value.size }!!
        val dayList = dayGroups.flatMap { it.days }.distinct().sortedBy { it.value }

        val dayLabel = if (dayList.size == 7) "ทุกวัน" else formatDays(dayList)
        return "$timeKey ($dayLabel)"
    }

    /**
     * Get current close time as "HH:MM" (when restaurant closes today).
     * Returns null if always open, or no closing today.
     */
    fun currentCloseTime(openingHours: String?, now: LocalDateTime = LocalDateTime.now()): String? {
        val segments = parse(openingHours) ?: return null
        if (segments.isEmpty()) return null

        val today = now.dayOfWeek
        val currentMinutes = now.hour * 60 + now.minute

        // Find the segment that contains current time
        for (segment in segments) {
            if (today !in segment.days) continue
            val isInside = if (segment.openMinutes <= segment.closeMinutes) {
                currentMinutes in segment.openMinutes..segment.closeMinutes
            } else {
                currentMinutes >= segment.openMinutes || currentMinutes <= segment.closeMinutes
            }
            if (isInside) {
                // Convert to today's local close (may cross midnight but display raw)
                return formatTime(segment.closeMinutes)
            }
        }
        return null
    }

    // ===== Private helpers =====

    private fun formatDays(days: List<DayOfWeek>): String {
        val sorted = days.sortedBy { it.value }
        // Check if all 7 days
        if (sorted.size == 7) return "ทุกวัน"
        // Check if contiguous range
        val isRange = sorted.zipWithNext().all { (a, b) -> a.value + 1 == b.value }
        return if (isRange && sorted.size >= 2) {
            "${THAI_DAY_SHORT[sorted.first()]}-${THAI_DAY_SHORT[sorted.last()]}"
        } else {
            sorted.joinToString(",") { THAI_DAY_SHORT[it] ?: "?" }
        }
    }

    private fun formatTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format(Locale.ROOT, "%02d:%02d", h, m)
    }

    private fun parse(openingHours: String?): List<Segment>? {
        if (openingHours.isNullOrBlank()) return null
        if (openingHours.trim() == "24/7") {
            // Always open: return a segment covering all days, 00:00-24:00
            return listOf(Segment(DayOfWeek.values().toSet(), 0, 24 * 60))
        }
        return openingHours.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() != "off" }
            .mapNotNull { parseSegment(it) }
    }

    private fun parseSegment(segment: String): Segment? {
        val parts = segment.split(" ", limit = 2)
        if (parts.isEmpty()) return null
        val days = parseDays(parts[0]) ?: return null

        // No time → open all day
        val timePart = if (parts.size > 1) parts[1] else return Segment(days, 0, 24 * 60)
        val times = timePart.split("-")
        if (times.size != 2) return null
        val open = parseTime(times[0].trim()) ?: return null
        val close = parseTime(times[1].trim()) ?: return null
        return Segment(days, open, close)
    }

    private fun parseDays(daysStr: String): Set<DayOfWeek>? {
        val days = mutableSetOf<DayOfWeek>()
        for (part in daysStr.split(",")) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                // Range: "Mo-Fr"
                val range = trimmed.split("-")
                if (range.size != 2) return null
                val start = DAY_MAP[range[0].trim()] ?: return null
                val end = DAY_MAP[range[1].trim()] ?: return null
                var current = start
                var safety = 0
                while (true) {
                    days.add(current)
                    if (current == end) break
                    current = current.plus(1)
                    if (++safety > 7) return null  // safety
                }
            } else {
                val day = DAY_MAP[trimmed] ?: return null
                days.add(day)
            }
        }
        return days.ifEmpty { null }
    }

    private fun parseTime(timeStr: String): Int? {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").find(timeStr) ?: return null
        val h = match.groupValues[1].toIntOrNull() ?: return null
        val m = match.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..24 || m !in 0..59) return null
        return h * 60 + m
    }

    private data class Segment(
        val days: Set<DayOfWeek>,
        val openMinutes: Int,
        val closeMinutes: Int,
    )
}
