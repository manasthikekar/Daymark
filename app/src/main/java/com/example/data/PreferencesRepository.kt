package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.calendarPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "daymark_preferences"
)

class PreferencesRepository(private val context: Context) {
    private val startWeekOnMondayKey = booleanPreferencesKey("start_week_on_monday")

    val startWeekOnMonday: Flow<Boolean> =
        context.calendarPreferencesDataStore.data.map { prefs ->
            prefs[startWeekOnMondayKey] ?: false
        }

    suspend fun setStartWeekOnMonday(enabled: Boolean) {
        context.calendarPreferencesDataStore.edit { prefs ->
            prefs[startWeekOnMondayKey] = enabled
        }
    }
}
