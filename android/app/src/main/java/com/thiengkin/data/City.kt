package com.thiengkin.data

/**
 * City — จุดหมาย/เมืองที่ user เลือก (Phase 1.5: รองรับ multi-city)
 *
 * ใช้สำหรับ:
 * 1. Fallback location เมื่อ GPS ไม่พร้อม
 * 2. Filter restaurants by city (Phase 1.5)
 * 3. Onboarding "เลือกเมือง" แรกเปิด
 * 4. Overpass API bbox — Phase 2 (ดึงร้านอาหารจาก OSM ในกรอบเมือง)
 */
data class City(
    val id: String,
    val nameTh: String,
    val nameEn: String,
    val lat: Double,
    val lng: Double,
    val emoji: String,
    /** Bounding box สำหรับ Overpass API (south, west, north, east). null = ไม่รองรับ OSM fetch. */
    val bbox: BoundingBox? = null,
)

/**
 * Bounding box — ใช้กับ Overpass API `query(bbox)`
 *
 * Format: (south, west, north, east) — ตาม Overpass spec
 * Example: Bangkok bbox = (13.65, 100.35, 13.90, 100.80)
 */
data class BoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    /** Format เป็น Overpass bbox string: "south,west,north,east" */
    fun toOverpassString(): String = "$south,$west,$north,$east"

    /** พื้นที่ (ตร.กม.) โดยประมาณ — ใช้เช็คว่า bbox ใหญ่เกินไป (Overpass จะ reject > ~0.5°) */
    val areaSqKm: Double
        get() {
            val midLat = (south + north) / 2.0
            val heightKm = (north - south) * 111.0
            val widthKm = (east - west) * 111.0 * kotlin.math.cos(Math.toRadians(midLat))
            return heightKm * widthKm
        }
}

/**
 * Cities — list เมืองไทยหลัก 10 เมือง (Phase 1.5 starter)
 *
 * Bbox ครอบคลุม "เขตเมือง/อำเภอเมือง" — ไม่ใช่ทั้งจังหวัด (กัน OSM result ใหญ่เกิน)
 * เพิ่มเมืองใหม่: เพิ่ม object ในนี้ + ใส่ bbox — Phase 2 อาจ migrate ไปยัง Supabase table
 */
object Cities {
    val BANGKOK = City(
        "bkk", "กรุงเทพมหานคร", "Bangkok",
        13.7563, 100.5018, "🏙️",
        bbox = BoundingBox(13.65, 100.35, 13.90, 100.80),  // ~600 sq.km (เขตกรุงเทพ)
    )
    val CHIANG_MAI = City(
        "cm", "เชียงใหม่", "Chiang Mai",
        18.7883, 98.9853, "🏔️",
        bbox = BoundingBox(18.72, 98.92, 18.84, 99.05),  // ~150 sq.km (เมืองเก่า+นิมมานฯ)
    )
    val CHIANG_RAI = City(
        "cr", "เชียงราย", "Chiang Rai",
        19.9105, 99.8406, "⛰️",
        bbox = BoundingBox(19.85, 99.80, 19.95, 99.90),
    )
    val PHUKET = City(
        "pkt", "ภูเก็ต", "Phuket",
        7.8804, 98.3923, "🏖️",
        bbox = BoundingBox(7.80, 98.30, 7.95, 98.45),  // เมืองภูเก็ต + ป่าตอง
    )
    val KRABI = City(
        "kbi", "กระบี่", "Krabi",
        8.0863, 98.9063, "🏝️",
        bbox = BoundingBox(8.02, 98.85, 8.15, 98.95),
    )
    val HAT_YAI = City(
        "hyd", "หาดใหญ่", "Hat Yai",
        7.0086, 100.4747, "🌴",
        bbox = BoundingBox(6.95, 100.40, 7.05, 100.50),
    )
    val NAKHON_RATCHASIMA = City(
        "kor", "นครราชสีมา", "Nakhon Ratchasima",
        14.9799, 102.0977, "🌾",
        bbox = BoundingBox(14.92, 102.05, 15.05, 102.15),
    )
    val KHON_KAEN = City(
        "kk", "ขอนแก่น", "Khon Kaen",
        16.4322, 102.8236, "🌿",
        bbox = BoundingBox(16.38, 102.78, 16.48, 102.88),
    )
    val PATTAYA = City(
        "pty", "พัทยา", "Pattaya",
        12.9236, 100.8825, "🌊",
        bbox = BoundingBox(12.88, 100.85, 12.97, 100.92),
    )
    val KOH_SAMUI = City(
        "smi", "เกาะสมุย", "Koh Samui",
        9.5018, 100.0136, "🌅",
        bbox = BoundingBox(9.45, 99.95, 9.55, 100.05),
    )

    /** All cities — order: กรุงเทพ ก่อน (default), ตามด้วยเมืองท่องเที่ยวหลัก */
    val all: List<City> = listOf(
        BANGKOK,
        CHIANG_MAI,
        CHIANG_RAI,
        PHUKET,
        KRABI,
        HAT_YAI,
        NAKHON_RATCHASIMA,
        KHON_KAEN,
        PATTAYA,
        KOH_SAMUI,
    )

    /** Default city เมื่อ first launch (Bangkok — เมืองหลวง) */
    val DEFAULT = BANGKOK

    fun findById(id: String?): City? = all.firstOrNull { it.id == id }

    /** Cities ที่รองรับ OSM fetch (มี bbox) — filter สำหรับ UI */
    val fetchable: List<City> = all.filter { it.bbox != null }
}
