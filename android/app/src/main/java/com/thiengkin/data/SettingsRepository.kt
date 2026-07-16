package com.thiengkin.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * SettingsRepository — persistent user settings (Phase 1 of M6)
 *
 * Phase 1 scope: เก็บ toggle/boolean เท่านั้น
 * - `autoDetectEnabled` (default true) — คุม GPS auto-detect province behavior
 *   - true  = ทุกครั้งที่เปิดแอป → re-detect province จาก GPS
 *   - false = จำจังหวัดที่ user เลือกเอง (manual pin)
 *
 * DataStore location: `Context.dataStore` extension ใช้ file `settings.preferences_pb`
 * ใน app's `filesDir/datastore/` — auto-managed by androidx.datastore
 *
 * Threading: DataStore handles dispatcher internally (Dispatchers.IO by default).
 * Read = Flow (cold, async) — collect จาก ViewModel
 * Write = suspend function — เรียกจาก coroutine
 *
 * Phase 2 (planned): เพิ่ม settings อื่นๆ เช่น
 * - defaultRadiusKm (Float)
 * - lastSelectedDistrictId (String?)
 * - favoriteCategories (Set<String>)
 */
class SettingsRepository(private val context: Context) {

    /**
     * Read flow: เริ่มต้น default `true` ถ้ายังไม่เคยตั้ง
     * ใช้ `catch` เพื่อกัน IOException (เช่น dataStore file เสีย) → fallback ไป default
     */
    val autoDetectEnabled: Flow<Boolean> = context.dataStore.data
        .catch { e ->
            Log.w(TAG, "DataStore read failed — falling back to default", e)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { prefs -> prefs[KEY_AUTO_DETECT] ?: DEFAULT_AUTO_DETECT }

    /**
     * Set toggle value — suspend (writes to disk)
     * เรียกจาก coroutine เท่านั้น
     */
    suspend fun setAutoDetectEnabled(enabled: Boolean) {
        Log.i(TAG, "setAutoDetectEnabled: $enabled")
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_DETECT] = enabled
        }
    }

    companion object {
        private const val TAG = "SettingsRepository"

        /** Default = true (เริ่มต้นให้ auto-detect เปิดไว้ — behavior เดิมของ M4) */
        const val DEFAULT_AUTO_DETECT = true

        private val KEY_AUTO_DETECT = booleanPreferencesKey("auto_detect_province")
    }
}

/** DataStore filename (ใน filesDir/datastore/) — public เพื่อให้ top-level extension ใช้ */
const val SETTINGS_DATASTORE_NAME = "settings"

/**
 * Top-level extension — DataStore singleton per Context (per docs recommendation)
 * https://developer.android.com/topic/libraries/architecture/datastore#create
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME,
)
