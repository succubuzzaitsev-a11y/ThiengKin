package com.thiengkin.data

import kotlinx.serialization.Serializable

/**
 * JSON schema ของ `assets/thailand-geography.json`
 *
 * สร้างจาก `data/thailand-geography.json` (M0 — chingchai/OpenGISData-Thailand)
 * Format: meta + regions[] + provinces[] + districts[]
 */
@Serializable
data class ThailandGeographyFile(
    val meta: GeographyMeta = GeographyMeta(),
    val regions: List<RegionDto> = emptyList(),
    val provinces: List<ProvinceDto> = emptyList(),
    val districts: List<DistrictDto> = emptyList(),
)

@Serializable
data class GeographyMeta(
    val version: Int = 1,
    val generatedAt: String? = null,
    val source: String? = null,
    val license: String? = null,
    val counts: Map<String, Int> = emptyMap(),
)

@Serializable
data class BboxDto(
    val s: Double,
    val w: Double,
    val n: Double,
    val e: Double,
)

@Serializable
data class CentroidDto(
    val lat: Double,
    val lng: Double,
)

@Serializable
data class RegionDto(
    val id: String,
    val nameEn: String,
    val nameTh: String? = null,
    val bbox: BboxDto = BboxDto(0.0, 0.0, 0.0, 0.0),
    val centroid: CentroidDto = CentroidDto(0.0, 0.0),
    val areaSqkm: Double = 0.0,
)

@Serializable
data class ProvinceDto(
    val id: String,
    val code: String,
    val nameTh: String,
    val nameEn: String,
    val regionNesdb: String? = null,
    val regionRoyin: String? = null,
    val bbox: BboxDto,
    val centroid: CentroidDto,
    val areaSqkm: Double? = null,
    val perimeterKm: Double? = null,
)

@Serializable
data class DistrictDto(
    val id: String,
    val code: String,
    val nameTh: String,
    val nameEn: String,
    val provinceId: String,
    val regionNesdb: String? = null,
    val regionRoyin: String? = null,
    val bbox: BboxDto,
    val centroid: CentroidDto,
    val areaSqkm: Double? = null,
    val perimeterKm: Double? = null,
)
