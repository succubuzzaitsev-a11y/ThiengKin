package com.thiengkin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Restaurant::class, Province::class, District::class],
    version = 4,  // v4: + Province, District tables + Restaurant.province_id, district_id
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class ThiengKinDatabase : RoomDatabase() {

    abstract fun restaurantDao(): RestaurantDao

    abstract fun provinceDao(): ProvinceDao

    abstract fun districtDao(): DistrictDao

    companion object {
        @Volatile
        private var INSTANCE: ThiengKinDatabase? = null

        fun get(context: Context): ThiengKinDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ThiengKinDatabase::class.java,
                    "thiengkin.db",
                )
                    .fallbackToDestructiveMigration()  // Phase 1 — ไม่มี user data จริง
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
