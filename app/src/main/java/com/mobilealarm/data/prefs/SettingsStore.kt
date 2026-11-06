package com.mobilealarm.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val cityName: String = "北京",
    val latitude: Double = 39.9042,
    val longitude: Double = 116.4074,
    val timezone: String = "Asia/Shanghai",
    val useAutoLocation: Boolean = false,
    val weatherEnabled: Boolean = true,
    val lastWeatherFetch: Long = 0L,
    val weatherCacheJson: String? = null,
    val remoteHolidaysJson: String? = null,
    val holidayOverridesJson: String? = null,
    val lastHolidayCheck: Long = 0L,
    val lastHolidayUpdate: Long = 0L,
    val notificationsAsked: Boolean = false,
    val amapKey: String = "",
    val commuteJson: String? = null,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val cityName = stringPreferencesKey("city_name")
        val latitude = doublePreferencesKey("latitude")
        val longitude = doublePreferencesKey("longitude")
        val timezone = stringPreferencesKey("timezone")
        val useAutoLocation = booleanPreferencesKey("use_auto_location")
        val weatherEnabled = booleanPreferencesKey("weather_enabled")
        val lastWeatherFetch = longPreferencesKey("last_weather_fetch")
        val weatherCache = stringPreferencesKey("weather_cache")
        val remoteHolidays = stringPreferencesKey("remote_holidays")
        val holidayOverrides = stringPreferencesKey("holiday_overrides")
        val lastHolidayCheck = longPreferencesKey("last_holiday_check")
        val lastHolidayUpdate = longPreferencesKey("last_holiday_update")
        val notificationsAsked = booleanPreferencesKey("notifications_asked")
        val amapKey = stringPreferencesKey("amap_key")
        val commuteJson = stringPreferencesKey("commute_json")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                cityName = prefs[Keys.cityName] ?: "北京",
                latitude = prefs[Keys.latitude] ?: 39.9042,
                longitude = prefs[Keys.longitude] ?: 116.4074,
                timezone = prefs[Keys.timezone] ?: "Asia/Shanghai",
                useAutoLocation = prefs[Keys.useAutoLocation] ?: false,
                weatherEnabled = prefs[Keys.weatherEnabled] ?: true,
                lastWeatherFetch = prefs[Keys.lastWeatherFetch] ?: 0L,
                weatherCacheJson = prefs[Keys.weatherCache],
                remoteHolidaysJson = prefs[Keys.remoteHolidays],
                holidayOverridesJson = prefs[Keys.holidayOverrides],
                lastHolidayCheck = prefs[Keys.lastHolidayCheck] ?: 0L,
                lastHolidayUpdate = prefs[Keys.lastHolidayUpdate] ?: 0L,
                notificationsAsked = prefs[Keys.notificationsAsked] ?: false,
                amapKey = prefs[Keys.amapKey] ?: "",
                commuteJson = prefs[Keys.commuteJson],
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setCity(name: String, latitude: Double, longitude: Double, timezone: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.cityName] = name
            prefs[Keys.latitude] = latitude
            prefs[Keys.longitude] = longitude
            prefs[Keys.timezone] = timezone
            prefs[Keys.useAutoLocation] = false
        }
    }

    suspend fun setAutoLocation(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.useAutoLocation] = enabled }
    }

    suspend fun setWeatherEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.weatherEnabled] = enabled }
    }

    suspend fun saveWeatherCache(json: String, fetchedAt: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.weatherCache] = json
            prefs[Keys.lastWeatherFetch] = fetchedAt
        }
    }

    suspend fun saveRemoteHolidays(json: String, updatedAt: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.remoteHolidays] = json
            prefs[Keys.lastHolidayUpdate] = updatedAt
            prefs[Keys.lastHolidayCheck] = updatedAt
        }
    }

    suspend fun touchHolidayCheck(at: Long) {
        context.settingsDataStore.edit { it[Keys.lastHolidayCheck] = at }
    }

    suspend fun saveHolidayOverrides(json: String) {
        context.settingsDataStore.edit { it[Keys.holidayOverrides] = json }
    }

    suspend fun setNotificationsAsked() {
        context.settingsDataStore.edit { it[Keys.notificationsAsked] = true }
    }

    suspend fun setAmapKey(key: String) {
        context.settingsDataStore.edit { it[Keys.amapKey] = key.trim() }
    }

    suspend fun saveCommuteJson(json: String) {
        context.settingsDataStore.edit { it[Keys.commuteJson] = json }
    }
}