package com.thiengkin.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Haversine distance — ระยะทางระหว่าง 2 จุดบนทรงกลม (โลก)
 *
 * ใช้สำหรับ:
 * - Near-me: filter ร้านในรัศมี N กม.
 * - Travel mode: เรียงลำดับจุดแวะตามระยะจาก route
 */
object Haversine {
    private const val EARTH_RADIUS_KM = 6371.0

    fun distanceKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }
}
