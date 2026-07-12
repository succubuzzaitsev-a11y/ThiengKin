package com.thiengkin

import android.app.Application
import android.util.Log
import com.thiengkin.data.DistrictDao
import com.thiengkin.data.GeographyRepository
import com.thiengkin.data.LocationRepository
import com.thiengkin.data.ProvinceDao
import com.thiengkin.data.RestaurantDao
import com.thiengkin.data.RestaurantRepository
import com.thiengkin.data.ThiengKinDatabase
import com.thiengkin.data.remote.FoursquareClient
import com.thiengkin.ui.screens.travel.TravelHomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ThiengKinApp — Application entry point
 *
 * Bootstraps:
 * 1. Room database (singleton)
 * 2. GeographyRepository — seed provinces + districts (M1.a — bundled JSON, runs once on first launch)
 * 3. Repository singleton (wired into TravelHomeViewModel.defaultRepository)
 *    - Phase 2: มี OsmClient + FoursquareClient (optional)
 * 4. LocationRepository (singleton — current GPS fix)
 *
 * M1.b:
 *  - Drop JsonImporter (no bundled seed — OSM on-demand only)
 *  - Wire ProvinceDao + DistrictDao into ViewModel
 *  - Set default province = Bangkok (load from DB after seed)
 *
 * Phase 1.5 (planned): replace singletons with Hilt @Inject
 */
class ThiengKinApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: ThiengKinDatabase
        private set

    lateinit var repository: RestaurantRepository
        private set

    lateinit var geographyRepository: GeographyRepository
        private set

    lateinit var locationRepository: LocationRepository
        private set

    /**
     * Default province id เมื่อ first launch — ใช้ Bangkok (id="bangkok") เป็น fallback ก่อน
     * ถ้า Bangkok ยังไม่ได้ seed (race condition) → set ทีหลังใน geography import callback
     */
    private val defaultProvinceId = "bangkok"

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.i(TAG, "ThiengKinApp onCreate")

        database = ThiengKinDatabase.get(this)
        val dao: RestaurantDao = database.restaurantDao()
        val provinceDao: ProvinceDao = database.provinceDao()
        val districtDao: DistrictDao = database.districtDao()

        // Foursquare client — null ถ้าไม่ได้ตั้ง API key
        val fsqKey = BuildConfig.FOURSQUARE_API_KEY
        val fsqClient = if (fsqKey.isNotBlank()) {
            Log.i(TAG, "Foursquare client ENABLED (key configured)")
            FoursquareClient(apiKey = fsqKey)
        } else {
            Log.w(TAG, "Foursquare client DISABLED (no API key in BuildConfig)")
            null
        }

        repository = RestaurantRepository(
            dao = dao,
            fsqClient = fsqClient,
        )
        geographyRepository = GeographyRepository(this, provinceDao, districtDao)
        locationRepository = LocationRepository(this)

        // Wire singletons into ViewModels (used as default params)
        TravelHomeViewModel.defaultRepository = repository
        TravelHomeViewModel.defaultLocationRepository = locationRepository
        TravelHomeViewModel.defaultProvinceDao = provinceDao
        TravelHomeViewModel.defaultDistrictDao = districtDao

        // First-launch: seed provinces + districts (M1.a — bundled JSON)
        // หลัง seed เสร็จ → set default province (Bangkok) ใน LocationRepository
        appScope.launch {
            val result = geographyRepository.importIfEmpty()
            Log.i(
                TAG,
                "Geography import: skipped=${result.skipped} provinces=${result.provinces} districts=${result.districts} error=${result.error}",
            )
            if (!result.skipped) {
                // Fresh seed — set default province
                setDefaultProvince(provinceDao)
            } else {
                // Already seeded — still ensure default province is set (in case kill app before user picked)
                if (locationRepository.getSelectedProvince() == null) {
                    setDefaultProvince(provinceDao)
                }
            }
        }
    }

    /** Set default province (Bangkok) — load from DB then push to LocationRepository */
    private suspend fun setDefaultProvince(provinceDao: ProvinceDao) {
        val province = provinceDao.getById(defaultProvinceId)
        if (province != null) {
            locationRepository.setSelectedProvince(province)
            Log.i(TAG, "Default province set: ${province.nameTh}")
        } else {
            Log.w(TAG, "Default province '$defaultProvinceId' not found in DB — check seed data")
        }
    }

    companion object {
        private const val TAG = "ThiengKinApp"

        @Volatile
        private var instance: ThiengKinApp? = null

        fun get(): ThiengKinApp = instance
            ?: throw IllegalStateException("ThiengKinApp not initialized yet")
    }
}
