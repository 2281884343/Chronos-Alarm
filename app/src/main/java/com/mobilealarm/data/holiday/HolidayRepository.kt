package com.mobilealarm.data.holiday

import android.content.Context
import com.mobilealarm.data.prefs.AppSettings
import com.mobilealarm.data.prefs.SettingsRepository
import com.mobilealarm.data.remote.httpGet
import com.mobilealarm.domain.holiday.HolidayJsonParser
import com.mobilealarm.domain.holiday.WorkdayCalendar
import com.mobilealarm.domain.holiday.YearHolidayData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.time.LocalDate
import java.time.Year

data class HolidayRefreshResult(
    val success: Boolean,
    val updatedYears: List<Int>,
    val failedYears: List<Int>,
    val message: String,
)

class HolidayRepository(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Volatile
    private var bundledCache: Map<Int, YearHolidayData>? = null

    val calendarFlow: Flow<WorkdayCalendar> = kotlinx.coroutines.flow.flow {
        val bundled = bundled()
        settings.settings.collect { emit(calendar(it, bundled)) }
    }

    suspend fun currentCalendar(): WorkdayCalendar = calendar(settings.current(), bundled())

    suspend fun bundled(): Map<Int, YearHolidayData> {
        bundledCache?.let { return it }
        val parsed = withContext(Dispatchers.IO) {
            val raw = context.assets.open("holidays.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            HolidayJsonParser.parseBundled(raw, source = "内置")
        }
        bundledCache = parsed
        return parsed
    }

    fun calendar(
        appSettings: AppSettings,
        bundled: Map<Int, YearHolidayData> = bundledCache ?: emptyMap(),
    ): WorkdayCalendar {
        val remote = parseRemoteRaw(appSettings.remoteHolidaysJson)
        val years = bundled.toMutableMap()
        years.putAll(remote)
        return WorkdayCalendar(years, parseOverrides(appSettings.holidayOverridesJson))
    }

    private fun parseRemoteRaw(raw: String?): Map<Int, YearHolidayData> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyMap()
        return root.mapNotNull { (yearKey, value) ->
            val year = yearKey.toIntOrNull() ?: return@mapNotNull null
            val text = runCatching { value.jsonPrimitive.content }.getOrNull() ?: return@mapNotNull null
            HolidayJsonParser.parseHolidayCn(text, year, source = "在线")?.let { year to it }
        }.toMap()
    }

    private fun parseOverrides(raw: String?): Map<LocalDate, Boolean> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyMap()
        return root.mapNotNull { (dateKey, value) ->
            val date = runCatching { LocalDate.parse(dateKey) }.getOrNull() ?: return@mapNotNull null
            val workday = runCatching { value.jsonPrimitive.booleanOrNull }.getOrNull() ?: return@mapNotNull null
            date to workday
        }.toMap()
    }

    suspend fun refresh(): HolidayRefreshResult {
        val now = Year.now().value
        val targets = (now - 1..now + 2).toList()
        val existingRaw = parseRemoteDocuments(settings.current().remoteHolidaysJson)
        val merged = existingRaw.toMutableMap()
        val updated = mutableListOf<Int>()
        val failed = mutableListOf<Int>()

        for (year in targets) {
            val url = "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$year.json"
            try {
                val text = httpGet(url)
                val parsed = HolidayJsonParser.parseHolidayCn(text, year, source = "在线")
                if (parsed == null) {
                    failed += year
                } else {
                    merged[year] = text
                    updated += year
                }
            } catch (error: IOException) {
                failed += year
            } catch (error: Exception) {
                failed += year
            }
        }

        val timestamp = System.currentTimeMillis()
        if (updated.isEmpty()) {
            settings.touchHolidayCheck(timestamp)
            return HolidayRefreshResult(
                success = false,
                updatedYears = emptyList(),
                failedYears = failed,
                message = "暂时没有获取到新的节假日数据，已保留本地日历。请检查网络后重试。",
            )
        }

        val document = JsonObject(
            merged.entries.associate { (year, raw) -> year.toString() to JsonPrimitive(raw) },
        )
        settings.saveRemoteHolidays(document.toString(), timestamp)
        return HolidayRefreshResult(
            success = true,
            updatedYears = updated.sorted(),
            failedYears = failed.sorted(),
            message = buildString {
                append("已更新：")
                append(updated.sorted().joinToString("、") { "$it 年" })
                if (failed.isNotEmpty()) {
                    append("；未发布/获取失败：")
                    append(failed.sorted().joinToString("、") { "$it 年" })
                }
            },
        )
    }

    private fun parseRemoteDocuments(raw: String?): Map<Int, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyMap()
        return root.mapNotNull { (yearKey, value) ->
            val year = yearKey.toIntOrNull() ?: return@mapNotNull null
            val text = runCatching { value.jsonPrimitive.content }.getOrNull() ?: return@mapNotNull null
            year to text
        }.toMap()
    }

    suspend fun setOverride(date: LocalDate, workday: Boolean?) {
        val settingsNow = settings.current()
        val overrides = parseOverrides(settingsNow.holidayOverridesJson).toMutableMap()
        if (workday == null) overrides.remove(date) else overrides[date] = workday
        val document = JsonObject(
            overrides.entries.associate { (date, value) -> date.toString() to JsonPrimitive(value) },
        )
        settings.saveHolidayOverrides(document.toString())
    }

    suspend fun overrides(): Map<LocalDate, Boolean> =
        parseOverrides(settings.current().holidayOverridesJson)

    suspend fun needsAnnualUpdate(): Boolean {
        val today = LocalDate.now()
        if (today.monthValue < 12 || today.dayOfMonth < 15) return false
        val year = today.year + 1
        val bundled = bundled()
        val remote = parseRemoteRaw(settings.current().remoteHolidaysJson)
        return year !in bundled && year !in remote
    }

    suspend fun yearSources(): Map<Int, String> {
        val appSettings = settings.settings.first()
        val bundled = bundled()
        val remote = parseRemoteRaw(appSettings.remoteHolidaysJson)
        return (bundled.keys + remote.keys).associateWith { year ->
            if (remote.containsKey(year)) "在线" else "内置"
        }
    }
}