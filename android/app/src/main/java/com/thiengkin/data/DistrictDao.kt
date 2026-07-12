package com.thiengkin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DistrictDao {

    @Query("SELECT * FROM districts WHERE province_id = :provinceId ORDER BY name_en ASC")
    fun observeByProvince(provinceId: String): Flow<List<District>>

    @Query("SELECT * FROM districts WHERE province_id = :provinceId ORDER BY name_en ASC")
    suspend fun getByProvince(provinceId: String): List<District>

    @Query("SELECT * FROM districts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): District?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(districts: List<District>)

    @Query("SELECT COUNT(*) FROM districts")
    suspend fun count(): Int
}
