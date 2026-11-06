package com.mobilealarm.data.weather

import com.mobilealarm.data.prefs.SettingsRepository
import com.mobilealarm.data.remote.OpenMeteoApi
import com.mobilealarm.domain.model.CityOption
import com.mobilealarm.domain.model.WeatherBundle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

data class WeatherRefreshResult(
    val success: Boolean,
    val message: String,
    val bundle: WeatherBundle? = null,
)

class WeatherRepository(
    private val settings: SettingsRepository,
    private val api: OpenMeteoApi,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val weatherFlow: Flow<WeatherBundle?> = settings.settings.map { appSettings ->
        decode(appSettings.weatherCacheJson)
    }

    suspend fun current(): WeatherBundle? = decode(settings.current().weatherCacheJson)

    suspend fun searchCity(query: String): List<CityOption> = api.geocode(query)

    suspend fun refresh(): WeatherRefreshResult {
        val appSettings = settings.current()
        if (!appSettings.weatherEnabled) {
            return WeatherRefreshResult(false, "天气联动已关闭，可在设置中开启")
        }
        return try {
            val bundle = api.forecast(appSettings.latitude, appSettings.longitude, appSettings.timezone)
                .copy(city = appSettings.cityName)
            settings.saveWeatherCache(json.encodeToString(WeatherBundle.serializer(), bundle), bundle.fetchedAt)
            WeatherRefreshResult(
                success = true,
                message = "已更新 ${bundle.city} 的天气：${bundle.days.size} 天预报",
                bundle = bundle,
            )
        } catch (error: IOException) {
            WeatherRefreshResult(false, error.message ?: "天气更新失败")
        } catch (error: Exception) {
            WeatherRefreshResult(false, "天气更新失败：${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun decode(raw: String?): WeatherBundle? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(WeatherBundle.serializer(), raw) }.getOrNull()
    }
}