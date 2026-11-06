package com.mobilealarm.domain.holiday

import com.mobilealarm.domain.model.DayInfo
import com.mobilealarm.domain.model.DayKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

data class HolidayRecord(
    val date: LocalDate,
    val isWorkday: Boolean,
    val name: String,
    val kind: DayKind,
    val source: String,
)

data class YearHolidayData(
    val year: Int,
    val records: Map<LocalDate, HolidayRecord>,
    val source: String,
)

@Serializable
private data class HolidayCnDoc(
    val year: Int? = null,
    val days: List<HolidayCnDay> = emptyList(),
)

@Serializable
private data class HolidayCnDay(
    val name: String = "",
    val date: String,
    @SerialName("isOffDay") val isOffDay: Boolean,
)

/**
 * 节假日 JSON 解析。
 *
 * 内置格式（沿用 WorkdayAlarm 的 holidays.json）：
 * {"years":{"2026":{"holidays":{"2026-01-01":"元旦"},"workdays":{"2026-01-04":"调休上班"}}}}
 *
 * 在线格式（holiday-cn）：
 * {"year":2026,"days":[{"name":"元旦","date":"2026-01-01","isOffDay":true}, ...]}
 */
object HolidayJsonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseBundled(raw: String, source: String = "内置"): Map<Int, YearHolidayData> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyMap()
        val years = root["years"]?.jsonObject ?: return emptyMap()
        val result = mutableMapOf<Int, YearHolidayData>()
        for ((yearKey, blockElement) in years) {
            val year = yearKey.toIntOrNull() ?: continue
            val block = blockElement as? JsonObject ?: continue
            val records = mutableMapOf<LocalDate, HolidayRecord>()
            readBundledMap(block["holidays"], year, isWorkday = false, nameFallback = "法定节假日", source, records)
            readBundledMap(block["workdays"], year, isWorkday = true, nameFallback = "调休上班", source, records)
            if (records.isNotEmpty()) {
                result[year] = YearHolidayData(year, records, source)
            }
        }
        return result
    }

    private fun readBundledMap(
        element: kotlinx.serialization.json.JsonElement?,
        year: Int,
        isWorkday: Boolean,
        nameFallback: String,
        source: String,
        into: MutableMap<LocalDate, HolidayRecord>,
    ) {
        val entries: List<Pair<String, String>> = when (element) {
            is JsonObject -> element.entries.map { (key, value) ->
                key to (runCatching { value.jsonPrimitive.content }.getOrDefault(""))
            }
            is JsonArray -> element.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                .map { it to "" }
            else -> emptyList()
        }
        for ((dateText, name) in entries) {
            val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull() ?: continue
            if (date.year != year) continue
            into[date] = HolidayRecord(
                date = date,
                isWorkday = isWorkday,
                name = name.ifBlank { nameFallback },
                kind = if (isWorkday) DayKind.MAKEUP else DayKind.HOLIDAY,
                source = source,
            )
        }
    }

    fun parseHolidayCn(raw: String, expectedYear: Int, source: String): YearHolidayData? {
        val doc = runCatching { json.decodeFromString<HolidayCnDoc>(raw) }.getOrNull() ?: return null
        val year = doc.year ?: expectedYear
        val records = doc.days.mapNotNull { item ->
            val date = runCatching { LocalDate.parse(item.date) }.getOrNull() ?: return@mapNotNull null
            HolidayRecord(
                date = date,
                isWorkday = !item.isOffDay,
                name = if (item.isOffDay) item.name.ifBlank { "法定节假日" } else "调休上班",
                kind = if (item.isOffDay) DayKind.HOLIDAY else DayKind.MAKEUP,
                source = source,
            )
        }.associateBy { it.date }
        if (records.isEmpty()) return null
        return YearHolidayData(year, records, source)
    }
}

/** 纯内存的工作日判定器，便于单元测试和跨线程读取。 */
class WorkdayCalendar(
    private val yearData: Map<Int, YearHolidayData> = emptyMap(),
    private val overrides: Map<LocalDate, Boolean> = emptyMap(),
) {
    val coveredYears: List<Int> get() = yearData.keys.sorted()

    fun isCovered(year: Int): Boolean = yearData.containsKey(year)

    fun info(date: LocalDate): DayInfo {
        val covered = yearData.containsKey(date.year)
        overrides[date]?.let { workday ->
            return DayInfo(
                date = date,
                isWorkday = workday,
                kind = if (workday) DayKind.OVERRIDE_WORK else DayKind.OVERRIDE_REST,
                name = "手动调整",
                source = "自定义",
                covered = covered,
            )
        }
        val record = yearData[date.year]?.records?.get(date)
        if (record != null) {
            return DayInfo(
                date = date,
                isWorkday = record.isWorkday,
                kind = record.kind,
                name = record.name,
                source = record.source,
                covered = true,
            )
        }
        val weekday = date.dayOfWeek.value
        return DayInfo(
            date = date,
            isWorkday = weekday <= 5,
            kind = if (weekday <= 5) DayKind.NORMAL else DayKind.WEEKEND,
            name = "",
            source = if (covered) "法定日历" else "周一至周五兜底",
            covered = covered,
        )
    }

    fun isWorkday(date: LocalDate): Boolean = info(date).isWorkday
}