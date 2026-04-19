package com.ngratzi.lumina.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ngratzi.lumina.data.model.LocationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        val KEY_TAILING_THRESHOLD    = doublePreferencesKey("tailing_tide_threshold_ft")
        val KEY_LOCATION_MODE        = stringPreferencesKey("location_mode")
        val KEY_MANUAL_LAT           = stringPreferencesKey("manual_lat")
        val KEY_MANUAL_LON           = stringPreferencesKey("manual_lon")
        val KEY_MANUAL_LOCATION_NAME = stringPreferencesKey("manual_location_name")

        val KEY_JEEP_ENABLED    = booleanPreferencesKey("jeep_enabled")
        val KEY_JEEP_MIN_TEMP   = intPreferencesKey("jeep_min_temp_f")
        val KEY_JEEP_MAX_TEMP   = intPreferencesKey("jeep_max_temp_f")
        val KEY_JEEP_MAX_RAIN   = intPreferencesKey("jeep_max_rain_pct")

        const val DEFAULT_TAILING_THRESHOLD = 5.8
        const val DEFAULT_JEEP_MIN_TEMP     = 65
        const val DEFAULT_JEEP_MAX_TEMP     = 88
        const val DEFAULT_JEEP_MAX_RAIN     = 20
    }

    val tailingTideThresholdFt: Flow<Double> = dataStore.data.map { prefs ->
        prefs[KEY_TAILING_THRESHOLD] ?: DEFAULT_TAILING_THRESHOLD
    }

    val locationMode: Flow<LocationMode> = dataStore.data.map { prefs ->
        when (prefs[KEY_LOCATION_MODE]) {
            LocationMode.MANUAL.name -> LocationMode.MANUAL
            else                     -> LocationMode.GPS
        }
    }

    val manualLat: Flow<Double?> = dataStore.data.map { prefs ->
        prefs[KEY_MANUAL_LAT]?.toDoubleOrNull()
    }

    val manualLon: Flow<Double?> = dataStore.data.map { prefs ->
        prefs[KEY_MANUAL_LON]?.toDoubleOrNull()
    }

    val manualLocationName: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_MANUAL_LOCATION_NAME] ?: ""
    }

    suspend fun setTailingTideThreshold(ft: Double) {
        dataStore.edit { prefs -> prefs[KEY_TAILING_THRESHOLD] = ft }
    }

    val jeepEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_JEEP_ENABLED] ?: false }
    val jeepMinTemp: Flow<Int>     = dataStore.data.map { it[KEY_JEEP_MIN_TEMP] ?: DEFAULT_JEEP_MIN_TEMP }
    val jeepMaxTemp: Flow<Int>     = dataStore.data.map { it[KEY_JEEP_MAX_TEMP] ?: DEFAULT_JEEP_MAX_TEMP }
    val jeepMaxRain: Flow<Int>     = dataStore.data.map { it[KEY_JEEP_MAX_RAIN] ?: DEFAULT_JEEP_MAX_RAIN }

    suspend fun setJeepEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_JEEP_ENABLED] = enabled }
    }
    suspend fun setJeepMinTemp(temp: Int) {
        dataStore.edit { it[KEY_JEEP_MIN_TEMP] = temp }
    }
    suspend fun setJeepMaxTemp(temp: Int) {
        dataStore.edit { it[KEY_JEEP_MAX_TEMP] = temp }
    }
    suspend fun setJeepMaxRain(pct: Int) {
        dataStore.edit { it[KEY_JEEP_MAX_RAIN] = pct }
    }

    suspend fun setLocationMode(mode: LocationMode) {
        dataStore.edit { prefs -> prefs[KEY_LOCATION_MODE] = mode.name }
    }

    suspend fun setManualLocation(name: String, lat: Double, lon: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_MANUAL_LOCATION_NAME] = name
            prefs[KEY_MANUAL_LAT]  = lat.toString()
            prefs[KEY_MANUAL_LON]  = lon.toString()
            prefs[KEY_LOCATION_MODE] = LocationMode.MANUAL.name
        }
    }
}
