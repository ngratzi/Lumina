package com.ngratzi.lumina.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        val KEY_TAILING_THRESHOLD = doublePreferencesKey("tailing_tide_threshold_ft")
        const val DEFAULT_TAILING_THRESHOLD = 5.8
    }

    val tailingTideThresholdFt: Flow<Double> = dataStore.data.map { prefs ->
        prefs[KEY_TAILING_THRESHOLD] ?: DEFAULT_TAILING_THRESHOLD
    }

    suspend fun setTailingTideThreshold(ft: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_TAILING_THRESHOLD] = ft
        }
    }
}
