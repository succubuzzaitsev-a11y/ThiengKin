package com.thiengkin.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository — single source of truth สำหรับ Restaurant data
 *
 * Phase 1: local-only (assets → Room)
 * Phase 2: เพิ่ม remote (Supabase Edge Function) แล้ว merge + sync
 */
class RestaurantRepository(
    private val dao: RestaurantDao,
) {
    fun observeTop(limit: Int = 10): Flow<List<Restaurant>> = dao.observeTop(limit)

    fun observeAll(): Flow<List<Restaurant>> = dao.observeAll()

    fun observeManualPicks(): Flow<List<Restaurant>> = dao.observeManualPicks()

    fun observeInBoundingBox(
        minLat: Double, maxLat: Double,
        minLng: Double, maxLng: Double,
    ): Flow<List<Restaurant>> = dao.observeInBoundingBox(minLat, maxLat, minLng, maxLng)

    fun observeById(id: String): Flow<Restaurant?> = dao.observeById(id)

    fun observeFavorites(): Flow<List<Restaurant>> = dao.observeFavorites()

    suspend fun getById(id: String): Restaurant? = dao.getById(id)

    suspend fun toggleFavorite(id: String) {
        val current = dao.getById(id) ?: return
        dao.setFavorite(id, !current.isFavorite)
    }
}
