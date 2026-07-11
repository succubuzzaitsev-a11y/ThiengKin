package com.thiengkin.data

/**
 * City — จุดหมาย/เมืองที่ user เลือก (Phase 1.5: รองรับ multi-city)
 *
 * ใช้สำหรับ:
 * 1. Fallback location เมื่อ GPS ไม่พร้อม
 * 2. Filter restaurants by city (Phase 1.5)
 * 3. Onboarding "เลือกเมือง" แรกเปิด
 */
data class City(
    val id: String,
    val nameTh: String,
    val nameEn: String,
    val lat: Double,
    val lng: Double,
    val emoji: String,
)

/**
 * Cities — list เมืองไทยหลัก 10 เมือง (Phase 1.5 starter)
 *
 * เพิ่มเมืองใหม่: เพิ่ม object ในนี้ — Phase 2 อาจ migrate ไปยัง Supabase table
 */
object Cities {
    val BANGKOK = City("bkk", "กรุงเทพมหานคร", "Bangkok", 13.7563, 100.5018, "🏙️")
    val CHIANG_MAI = City("cm", "เชียงใหม่", "Chiang Mai", 18.7883, 98.9853, "🏔️")
    val CHIANG_RAI = City("cr", "เชียงราย", "Chiang Rai", 19.9105, 99.8406, "⛰️")
    val PHUKET = City("pkt", "ภูเก็ต", "Phuket", 7.8804, 98.3923, "🏖️")
    val KRABI = City("kbi", "กระบี่", "Krabi", 8.0863, 98.9063, "🏝️")
    val HAT_YAI = City("hyd", "หาดใหญ่", "Hat Yai", 7.0086, 100.4747, "🌴")
    val NAKHON_RATCHASIMA = City("kor", "นครราชสีมา", "Nakhon Ratchasima", 14.9799, 102.0977, "🌾")
    val KHON_KAEN = City("kk", "ขอนแก่น", "Khon Kaen", 16.4322, 102.8236, "🌿")
    val PATTAYA = City("pty", "พัทยา", "Pattaya", 12.9236, 100.8825, "🌊")
    val KOH_SAMUI = City("smi", "เกาะสมุย", "Koh Samui", 9.5018, 100.0136, "🌅")

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
}
