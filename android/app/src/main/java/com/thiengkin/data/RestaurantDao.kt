package com.thiengkin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RestaurantDao {

    // === Bulk insert (initial import) ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(restaurants: List<Restaurant>)

    @Query("SELECT COUNT(*) FROM restaurants")
    suspend fun count(): Int

    // === Single ===
    @Query("SELECT * FROM restaurants WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Restaurant?

    @Query("SELECT * FROM restaurants WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<Restaurant?>

    // === Lists ===
    @Query("SELECT * FROM restaurants WHERE source = 'manual' ORDER BY rating DESC, review_count DESC")
    fun observeManualPicks(): Flow<List<Restaurant>>

    @Query("SELECT * FROM restaurants ORDER BY rating DESC, review_count DESC LIMIT :limit")
    fun observeTop(limit: Int = 10): Flow<List<Restaurant>>

    @Query("SELECT * FROM restaurants")
    fun observeAll(): Flow<List<Restaurant>>

    @Query("""
        SELECT * FROM restaurants
        WHERE lat BETWEEN :minLat AND :maxLat
          AND lng BETWEEN :minLng AND :maxLng
        ORDER BY rating DESC, review_count DESC
    """)
    fun observeInBoundingBox(
        minLat: Double, maxLat: Double,
        minLng: Double, maxLng: Double,
    ): Flow<List<Restaurant>>

    // === Phase 3 (M1): Province/District-scoped queries — หลักใน nationwide mode ===

    /**
     * ดึงร้านตาม province_id (ทั้ง province — ไม่ filter district)
     * - provinceId="" → ดึง manual (ไม่มี province) เท่านั้น
     * - provinceId="bangkok" + source=null → ทั้งหมดในกรุงเทพ
     * - provinceId="bangkok" + source="osm" → OSM เท่านั้น
     */
    @Query("""
        SELECT * FROM restaurants
        WHERE (:provinceId = '' OR province_id = :provinceId OR (province_id = '' AND source = 'manual'))
          AND (:source IS NULL OR source = :source)
        ORDER BY rating DESC, review_count DESC
    """)
    fun observeByProvince(provinceId: String, source: String? = null): Flow<List<Restaurant>>

    /**
     * ดึงร้านในอำเภอเฉพาะ (drill-down)
     * - districtId="" → ไม่ควรเรียก (ใช้ observeByProvince แทน)
     * - districtId="phra_nakhon" → ร้านในเขตพระนคร
     */
    @Query("""
        SELECT * FROM restaurants
        WHERE district_id = :districtId
          AND (:source IS NULL OR source = :source)
        ORDER BY rating DESC, review_count DESC
    """)
    fun observeByDistrict(districtId: String, source: String? = null): Flow<List<Restaurant>>

    @Query("SELECT COUNT(*) FROM restaurants WHERE province_id = :provinceId AND source = :source")
    suspend fun countByProvinceAndSource(provinceId: String, source: String): Int

    @Query("SELECT MAX(source_updated_at) FROM restaurants WHERE province_id = :provinceId AND source = :source")
    suspend fun latestUpdateByProvinceAndSource(provinceId: String, source: String): Long?

    @Query("DELETE FROM restaurants WHERE province_id = :provinceId AND source = :source")
    suspend fun deleteByProvinceAndSource(provinceId: String, source: String): Int

    // === Favorites ===
    @Query("SELECT * FROM restaurants WHERE is_favorite = 1 ORDER BY name ASC")
    fun observeFavorites(): Flow<List<Restaurant>>

    @Update
    suspend fun update(restaurant: Restaurant)

    @Query("UPDATE restaurants SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)
}
