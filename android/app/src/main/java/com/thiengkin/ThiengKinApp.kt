package com.thiengkin

import android.app.Application
import android.util.Log
import com.thiengkin.data.Cities
import com.thiengkin.data.GeographyRepository
import com.thiengkin.data.JsonImporter
import com.thiengkin.data.LocationRepository
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
 * 2. JSON importer (runs once on first launch)
 * 3. Repository singleton (wired into TravelHomeViewModel.defaultRepository)
 *    - Phase 2: มี OsmClient + FoursquareClient (optional)
 * 4. LocationRepository (singleton — current GPS fix)
 *
 * Phase 1.5 (planned): replace singletons with Hilt @Inject
 */
class ThiengKinApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: ThiengKinDatabase
        private set

    lateinit var repository: RestaurantRepository
        private set

    lateinit var jsonImporter: JsonImporter
        private set

    lateinit var geographyRepository: GeographyRepository
        private set

    lateinit var locationRepository: LocationRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.i(TAG, "ThiengKinApp onCreate")

        database = ThiengKinDatabase.get(this)
        val dao: RestaurantDao = database.restaurantDao()
        val provinceDao = database.provinceDao()
        val districtDao = database.districtDao()

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
        jsonImporter = JsonImporter(this, dao)
        geographyRepository = GeographyRepository(this, provinceDao, districtDao)
        locationRepository = LocationRepository(this)

        // Wire singletons into ViewModels (used as default params)
        TravelHomeViewModel.defaultRepository = repository
        TravelHomeViewModel.defaultLocationRepository = locationRepository

        // First-launch: seed provinces + districts (M1.a — bundled JSON)
        appScope.launch {
            val result = geographyRepository.importIfEmpty()
            Log.i(
                TAG,
                "Geography import: skipped=${result.skipped} provinces=${result.provinces} districts=${result.districts} error=${result.error}",
            )
        }

        // First-launch import (manual seed from assets/seed-restaurants.json) — legacy, will be removed in M1.b
        appScope.launch {
            val result = jsonImporter.importIfEmpty()
            Log.i(
                TAG,
                "JSON import: skipped=${result.skipped} count=${result.count} error=${result.error}",
            )
        }

        // Set default city — Phase 1.5 ใช้ Bangkok เป็น fallback เริ่มต้น
        // (Phase 2: เปลี่ยนเป็นโหลดจาก DataStore ตามที่ user เลือกไว้ครั้งล่าสุด)
        locationRepository.setSelectedCity(Cities.DEFAULT)
        Log.i(TAG, "Default city set: ${Cities.DEFAULT.nameTh}")
    }

    companion object {
        private const val TAG = "ThiengKinApp"

        @Volatile
        private var instance: ThiengKinApp? = null

        fun get(): ThiengKinApp = instance
            ?: throw IllegalStateException("ThiengKinApp not initialized yet")
    }
}
