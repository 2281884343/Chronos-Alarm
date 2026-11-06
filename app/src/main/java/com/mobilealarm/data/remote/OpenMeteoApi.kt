package com.mobilealarm.data.remote

import com.mobilealarm.domain.model.CityOption
import com.mobilealarm.domain.model.WeatherBundle
import com.mobilealarm.domain.model.WeatherDay
import com.mobilealarm.domain.model.WeatherHour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Open-Meteo 免费接口（无需 API Key）。
 * 文档：https://open-meteo.com/en/docs
 */
class OpenMeteoApi {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun geocode(query: String): List<CityOption> = withContext(Dispatchers.IO) {
        val keyword = query.trim()
        if (keyword.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=$encoded&count=8&language=zh&format=json"
        val root = parseObject(httpGet(url))
        val results = root["results"] as? JsonArray ?: return@withContext emptyList()
        results.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val name = item.string("name") ?: return@mapNotNull null
            val lat = item.double("latitude") ?: return@mapNotNull null
            val lon = item.double("longitude") ?: return@mapNotNull null
            CityOption(
                name = name,
                latitude = lat,
                longitude = lon,
                admin1 = item.string("admin1"),
                country = item.string("country"),
                timezone = item.string("timezone"),
            )
        }
    }

    suspend fun forecast(latitude: Double, longitude: Double, timezone: String?): WeatherBundle =
        withContext(Dispatchers.IO) {
            val lat = "%.4f".format(latitude)
            val lon = "%.4f".format(longitude)
            val zone = if (timezone.isNullOrBlank()) "auto" else timezone
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
                "precipitation_probability_max,wind_speed_10m_max,uv_index_max" +
                "&hourly=temperature_2m,precipitation_probability,weather_code,wind_speed_10m" +
                "&timezone=${URLEncoder.encode(zone, "UTF-8")}" +
                "&forecast_days=16&wind_speed_unit=kmh"
            val root = parseObject(httpGet(url))
            val daily = root["daily"]?.jsonObject
                ?: throw IOException("天气接口返回格式异常：缺少 daily 数据")
            val hourly = root["hourly"] as? JsonObject
            val dates = daily.stringArray("time")
            if (dates.isEmpty()) throw IOException("天气接口未返回预报日期")

            val hourlyByDay = mutableMapOf<LocalDate, MutableMap<Int, WeatherHour>>()
            if (hourly != null) {
                val times = hourly.stringArray("time")
                val temps = hourly.doubleArray("temperature_2m")
                val probs = hourly.doubleArray("precipitation_probability")
                val codes = hourly.intArray("weather_code")
                val winds = hourly.doubleArray("wind_speed_10m")
                times.forEachIndexed { index, text ->
                    val dateTime = runCatching { LocalDateTime.parse(text) }.getOrNull() ?: return@forEachIndexed
                    val hour = WeatherHour(
                        code = codes.getOrNull(index),
                        temperature = temps.getOrNull(index),
                        precipProbability = probs.getOrNull(index),
                        windSpeed = winds.getOrNull(index),
                    )
                    hourlyByDay.getOrPut(dateTime.toLocalDate()) { mutableMapOf() }[dateTime.hour] = hour
                }
            }

            val codes = daily.intArray("weather_code")
            val maxes = daily.doubleArray("temperature_2m_max")
            val mins = daily.doubleArray("temperature_2m_min")
            val sums = daily.doubleArray("precipitation_sum")
            val probs = daily.doubleArray("precipitation_probability_max")
            val winds = daily.doubleArray("wind_speed_10m_max")
            val uvs = daily.doubleArray("uv_index_max")

            val days = dates.mapIndexedNotNull { index, text ->
                val date = runCatching { LocalDate.parse(text) }.getOrNull() ?: return@mapIndexedNotNull null
                WeatherDay(
                    date = date,
                    code = codes.getOrNull(index),
                    tempMax = maxes.getOrNull(index),
                    tempMin = mins.getOrNull(index),
                    precipProbMax = probs.getOrNull(index),
                    precipSum = sums.getOrNull(index),
                    windMax = winds.getOrNull(index),
                    uvMax = uvs.getOrNull(index),
                    hourly = hourlyByDay[date].orEmpty(),
                )
            }
            if (days.isEmpty()) throw IOException("天气接口未返回有效预报")

            WeatherBundle(
                city = "",
                latitude = root.double("latitude") ?: latitude,
                longitude = root.double("longitude") ?: longitude,
                timezone = root.string("timezone") ?: timezone ?: "auto",
                fetchedAt = System.currentTimeMillis(),
                days = days,
            )
        }

    private fun parseObject(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw IOException("天气接口返回了无法解析的数据") }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonElement)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonElement)?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.stringArray(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()

    private fun JsonObject.doubleArray(key: String): List<Double?> =
        (this[key] as? JsonArray)?.map { it.jsonPrimitive.doubleOrNull } ?: emptyList()

    private fun JsonObject.intArray(key: String): List<Int?> =
        (this[key] as? JsonArray)?.map { it.jsonPrimitive.intOrNull } ?: emptyList()
}