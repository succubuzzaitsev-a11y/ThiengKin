package com.thiengkin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProvinceDao {

    @Query("SELECT * FROM provinces ORDER BY name_en ASC")
    fun observeAll(): Flow<List<Province>>

    @Query("SELECT * FROM provinces ORDER BY name_en ASC")
    suspend fun getAll(): List<Province>

    @Query("SELECT * FROM provinces WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Province?

    @Query("SELECT * FROM provinces WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): Province?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(provinces: List<Province>)

    @Query("SELECT COUNT(*) FROM provinces")
    suspend fun count(): Int
}
