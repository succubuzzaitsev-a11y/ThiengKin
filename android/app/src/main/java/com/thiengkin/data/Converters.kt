package com.thiengkin.data

import androidx.room.TypeConverter

/**
 * Room TypeConverters — แปลง List<String> (tags) ↔ String (CSV)
 * ใช้ ", " เป็น delimiter (safe เพราะ tag ไม่มี comma ใน dataset)
 */
class Converters {
    @TypeConverter
    fun listToString(list: List<String>?): String =
        list?.joinToString(", ") ?: ""

    @TypeConverter
    fun stringToList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList()
        else value.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
}
