package com.thiengkin.data

/**
 * Shared restaurant category definitions.
 *
 * Used by:
 *  - [com.thiengkin.ui.screens.travel.TravelHomeViewModel.CATEGORY_FILTERS] (M2.1 CategoryGrid)
 *  - [com.thiengkin.ui.screens.nearme.NearMeViewModel] (Near Me category chips)
 *
 * Each category has:
 *  - [key]    : stable identifier (English, snake-friendly)
 *  - [label]  : Thai display name for chips / grid items
 *  - [matches]: predicate ตรวจว่าร้านตรงกับหมวดนี้ไหม
 *
 * Strategy: name-based matching (substring) + category field check.
 * SerpApi / OSM เก็บ `category="ร้านอาหาร"` (generic) แต่ชื่อร้านมี keyword เฉพาะ
 * (เช่น "ก๋วยเตี๋ยวเรือใหญ่" → match "ก๋วยเตี๋ยว") เลยต้อง match ทั้ง 2 ทาง
 */
object RestaurantCategories {

    /** Stable keys — used as ID in CategoryGrid state, predicate lookup, analytics */
    val keys: List<String> = listOf(
        "noodle", "rice", "cafe", "fastfood", "bakery",
        "papaya", "salad", "pub", "dessert", "late",
    )

    /** Thai display labels (parallel to [keys], same order) */
    val labels: List<String> = listOf(
        "ก๋วยเตี๋ยว", "ข้าวราดแกง", "คาเฟ่", "ฟาสต์ฟู้ด", "เบเกอรี่",
        "ส้มตำ", "สลัด", "ผับบาร์", "ของหวาน", "เปิดดึก",
    )

    /**
     * Category key → predicate (Restaurant → Boolean)
     * ใช้ name substring + category field + tag check
     */
    val predicates: Map<String, (Restaurant) -> Boolean> = mapOf(
        // ก๋วยเตี๋ยว — noodle shops (boat noodles, ramen, บะหมี่, etc.)
        "noodle" to { r ->
            r.category == "ร้านอาหาร" && (
                r.name.contains("ก๋วยเตี๋ยว") ||
                    r.name.contains("บะหมี่") ||
                    r.name.contains("ราเมง") ||
                    r.name.contains("เฝอ") ||
                    r.name.contains("boat noodle", ignoreCase = true) ||
                    r.tags.any { it.contains("noodle") || it.contains("ramen") }
                )
        },
        // ข้าวราดแกง — rice + curry shops (khao man gai, khao soi, etc.)
        "rice" to { r ->
            r.category == "ร้านอาหาร" && (
                r.name.contains("ข้าว") ||
                    r.name.contains("khao", ignoreCase = true) ||
                    r.name.contains("rice", ignoreCase = true) ||
                    r.tags.any { it.contains("rice") }
                )
        },
        // คาเฟ่ — coffee shops (specialty coffee, café)
        "cafe" to { r ->
            r.category == "คาเฟ่" ||
                r.name.contains("กาแฟ") ||
                r.name.contains("coffee", ignoreCase = true) ||
                r.name.contains("cafe", ignoreCase = true) ||
                r.name.contains("คาเฟ่") ||
                r.tags.any { it.contains("coffee") || it.contains("cafe") }
        },
        // ฟาสต์ฟู้ด — fast food (burger, pizza, chain)
        "fastfood" to { r ->
            r.category == "ฟาสต์ฟู้ด" ||
                r.name.contains("burger", ignoreCase = true) ||
                r.name.contains("pizza", ignoreCase = true) ||
                r.name.contains("fast", ignoreCase = true) ||
                r.tags.any { it.contains("burger") || it.contains("pizza") || it.contains("fast_food") }
        },
        // เบเกอรี่ — bakery (sourdough, pastries, cake)
        "bakery" to { r ->
            val name = r.name
            name.contains("เบเกอรี่") ||
                name.contains("เค้ก") ||
                name.contains("ขนมปัง") ||
                name.contains("bakery", ignoreCase = true) ||
                name.contains("cake", ignoreCase = true) ||
                name.contains("bake", ignoreCase = true) ||
                r.tags.any { it.contains("bakery") || it.contains("pastry") }
        },
        // ส้มตำ — som tam / papaya salad
        "papaya" to { r ->
            r.name.contains("ส้มตำ") ||
                r.name.contains("ตำ") ||
                r.name.contains("som tam", ignoreCase = true) ||
                r.name.contains("papaya", ignoreCase = true) ||
                r.tags.any { it.contains("papaya") }
        },
        // สลัด — salad / healthy
        "salad" to { r ->
            r.name.contains("สลัด") ||
                r.name.contains("salad", ignoreCase = true) ||
                r.tags.any { it.contains("salad") }
        },
        // ผับบาร์ — pub / bar / nightclub
        "pub" to { r ->
            r.name.contains("ผับ") ||
                r.name.contains("บาร์") ||
                r.name.contains("pub", ignoreCase = true) ||
                r.name.contains("bar", ignoreCase = true) ||
                r.tags.any { it == "amenity:pub" || it == "amenity:bar" || it == "amenity:nightclub" }
        },
        // ของหวาน — dessert / ice cream / bingsu
        "dessert" to { r ->
            r.tags.any { it.contains("ice_cream") } ||
                r.name.contains("ของหวาน") ||
                r.name.contains("ไอศกรีม") ||
                r.name.contains("dessert", ignoreCase = true) ||
                r.name.contains("bingsu", ignoreCase = true) ||
                r.name.contains("after you", ignoreCase = true)
        },
        // เปิดดึก — late night (open after 22:00 or late-night keywords)
        "late" to { r ->
            // Match by name (bar/pub) OR check opening hours for late close
            r.name.contains("บาร์") || r.name.contains("bar", ignoreCase = true) ||
                r.name.contains("ผับ") || r.name.contains("pub", ignoreCase = true) ||
                r.tags.any { it.contains("bar") || it.contains("pub") || it.contains("nightclub") } ||
                OpeningHoursParser.formatForDisplay(r.openingHours)?.let { hours ->
                    hours.contains("22:") || hours.contains("23:") || hours.contains("00:") ||
                        hours.contains("01:") || hours.contains("02:")
                } ?: false
        },
    )

    /**
     * Get label for a key (e.g. "noodle" → "ก๋วยเตี๋ยว")
     * Returns the key itself if not found.
     */
    fun labelFor(key: String): String {
        val idx = keys.indexOf(key)
        return if (idx >= 0) labels[idx] else key
    }

    /**
     * Get key for a Thai label (e.g. "ก๋วยเตี๋ยว" → "noodle")
     * Returns the label itself if not found.
     */
    fun keyFor(label: String): String {
        val idx = labels.indexOf(label)
        return if (idx >= 0) keys[idx] else label
    }
}
