package com.mobilealarm.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.ceil

/** 一天的官方属性。 */
enum class DayKind {
    NORMAL,
    MAKEUP,
    HOLIDAY,
    WEEKEND,
    OVERRIDE_WORK,
    OVERRIDE_REST,
}

data class DayInfo(
    val date: LocalDate,
    val isWorkday: Boolean,
    val kind: DayKind,
    val name: String = "",
    val source: String = "",
    val covered: Boolean = true,
) {
    val kindLabel: String
        get() = when (kind) {
            DayKind.NORMAL -> "工作日"
            DayKind.MAKEUP -> "调休上班"
            DayKind.HOLIDAY -> name.ifBlank { "法定节假日" }
            DayKind.WEEKEND -> "周末休息"
            DayKind.OVERRIDE_WORK -> "手动设为上班"
            DayKind.OVERRIDE_REST -> "手动设为休息"
        }

    val shortLabel: String
        get() = when (kind) {
            DayKind.MAKEUP, DayKind.OVERRIDE_WORK -> "班"
            DayKind.HOLIDAY, DayKind.WEEKEND, DayKind.OVERRIDE_REST -> "休"
            DayKind.NORMAL -> "班"
        }

    val weekdayZh: String
        get() = WEEKDAYS[date.dayOfWeek.value - 1]

    companion object {
        val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }
}

/** 重复规则。 */
enum class DayMode(val label: String, val description: String) {
    ONCE("只响一次", "在选定日期响铃一次，响后自动关闭"),
    WORKDAY("工作日", "法定工作日，含调休补班，跳过节假日"),
    EVERY_DAY("每天", "一年 365 天都响"),
    REST_DAY("休息日", "周末与法定节假日响铃"),
    CUSTOM("自定义", "选择星期，可跟随法定调休"),
}

enum class WeatherGroup(val label: String, val emoji: String, val codes: Set<Int>) {
    CLEAR("晴", "☀️", setOf(0, 1)),
    CLOUDY("多云/阴", "☁️", setOf(2, 3)),
    FOG("雾", "🌫️", setOf(45, 48)),
    RAIN("雨", "🌧️", setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)),
    SNOW("雪", "❄️", setOf(71, 73, 75, 77, 85, 86)),
    STORM("雷暴", "⛈️", setOf(95, 96, 99));

    companion object {
        fun fromCode(code: Int?): WeatherGroup? = entries.firstOrNull { code != null && code in it.codes }
    }
}

object WeatherCodes {
    fun label(code: Int?): String = when (code) {
        0 -> "晴"
        1 -> "大部晴朗"
        2 -> "多云"
        3 -> "阴"
        45 -> "雾"
        48 -> "雾凇"
        51 -> "小毛毛雨"
        53 -> "毛毛雨"
        55 -> "大毛毛雨"
        56 -> "冻毛毛雨"
        57 -> "强冻毛毛雨"
        61 -> "小雨"
        63 -> "中雨"
        65 -> "大雨"
        66 -> "冻雨"
        67 -> "强冻雨"
        71 -> "小雪"
        73 -> "中雪"
        75 -> "大雪"
        77 -> "雪粒"
        80 -> "小阵雨"
        81 -> "阵雨"
        82 -> "强阵雨"
        85 -> "小阵雪"
        86 -> "强阵雪"
        95 -> "雷阵雨"
        96 -> "雷阵雨伴冰雹"
        99 -> "强雷暴伴冰雹"
        else -> "未知"
    }

    fun emoji(code: Int?): String = WeatherGroup.fromCode(code)?.emoji ?: "🌡️"

    fun isSevere(code: Int?): Boolean =
        code != null && (code in 65..67 || code in 75..77 || code in 82..86 || code >= 95)
}

enum class WeatherMetric(val label: String, val unit: String, val isGroup: Boolean = false) {
    PRECIP_PROB("降水概率", "%"),
    WEATHER_GROUP("天气现象", "", isGroup = true),
    TEMPERATURE("闹钟时刻气温", "°C"),
    TEMP_MIN("当天最低气温", "°C"),
    TEMP_MAX("当天最高气温", "°C"),
    WIND_SPEED("闹钟时刻风速", "km/h"),
    PRECIP_MM("当天降水量", "mm"),
    UV_INDEX("当天紫外线指数", ""),
}

enum class RuleComparator(val label: String) {
    GTE("≥"),
    LTE("≤"),
    IN("属于"),
    NOT_IN("不属于"),
}

@Serializable
data class WeatherRule(
    val id: String = UUID.randomUUID().toString(),
    val metric: WeatherMetric = WeatherMetric.PRECIP_PROB,
    val comparator: RuleComparator = RuleComparator.GTE,
    val threshold: Double = 60.0,
    val groups: List<WeatherGroup> = emptyList(),
    /** 负数 = 提前，正数 = 延后，单位分钟。 */
    val offsetMinutes: Int = -15,
    val enabled: Boolean = true,
) {
    val offsetLabel: String
        get() = when {
            offsetMinutes == 0 -> "不调整"
            offsetMinutes < 0 -> "提前 ${-offsetMinutes} 分钟"
            else -> "延后 $offsetMinutes 分钟"
        }

    fun conditionText(): String {
        if (metric.isGroup) {
            val names = if (groups.isEmpty()) "（未选择）" else groups.joinToString("、") { it.label }
            return "天气现象 ${comparator.label} $names"
        }
        val value = if (threshold % 1.0 == 0.0) threshold.toInt().toString() else threshold.toString()
        return "${metric.label} ${comparator.label} $value${metric.unit}"
    }

    fun sentence(): String = "如果 ${conditionText()}，就${offsetLabel}"
}

@Serializable
data class WeatherHour(
    val code: Int? = null,
    val temperature: Double? = null,
    val precipProbability: Double? = null,
    val windSpeed: Double? = null,
)

@Serializable
data class WeatherDay(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val code: Int? = null,
    val tempMax: Double? = null,
    val tempMin: Double? = null,
    val precipProbMax: Double? = null,
    val precipSum: Double? = null,
    val windMax: Double? = null,
    val uvMax: Double? = null,
    val hourly: Map<Int, WeatherHour> = emptyMap(),
)

@Serializable
data class WeatherPoint(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val hour: Int,
    val code: Int?,
    val temperature: Double?,
    val precipProbability: Double?,
    val windSpeed: Double?,
    val tempMin: Double?,
    val tempMax: Double?,
    val precipSum: Double?,
    val uvIndex: Double?,
) {
    val group: WeatherGroup? get() = WeatherGroup.fromCode(code)
    val codeLabel: String get() = WeatherCodes.label(code)
    val emoji: String get() = WeatherCodes.emoji(code)

    fun valueOf(metric: WeatherMetric): Double? = when (metric) {
        WeatherMetric.PRECIP_PROB -> precipProbability
        WeatherMetric.TEMPERATURE -> temperature
        WeatherMetric.TEMP_MIN -> tempMin
        WeatherMetric.TEMP_MAX -> tempMax
        WeatherMetric.WIND_SPEED -> windSpeed
        WeatherMetric.PRECIP_MM -> precipSum
        WeatherMetric.UV_INDEX -> uvIndex
        WeatherMetric.WEATHER_GROUP -> null
    }
}

@Serializable
data class WeatherBundle(
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val fetchedAt: Long,
    val days: List<WeatherDay>,
) {
    fun dayFor(date: LocalDate): WeatherDay? = days.firstOrNull { it.date == date }

    fun pointFor(date: LocalDate, hour: Int): WeatherPoint? {
        val day = dayFor(date) ?: return null
        val hourly = day.hourly[hour]
        return WeatherPoint(
            date = date,
            hour = hour,
            code = hourly?.code ?: day.code,
            temperature = hourly?.temperature,
            precipProbability = hourly?.precipProbability ?: day.precipProbMax,
            windSpeed = hourly?.windSpeed ?: day.windMax,
            tempMin = day.tempMin,
            tempMax = day.tempMax,
            precipSum = day.precipSum,
            uvIndex = day.uvMax,
        )
    }

    val lastDay: LocalDate? get() = days.lastOrNull()?.date
}

data class Alarm(
    val id: Long = 0,
    val label: String = "",
    val hour: Int = 7,
    val minute: Int = 30,
    val enabled: Boolean = true,
    val dayMode: DayMode = DayMode.WORKDAY,
    val customDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** 仅 [DayMode.ONCE] 使用；其它模式忽略。 */
    val oneTimeDate: LocalDate? = null,
    val observeHolidays: Boolean = true,
    val weatherRules: List<WeatherRule> = emptyList(),
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
    val ringtone: String? = null,
) {
    val timeText: String get() = "%02d:%02d".format(hour, minute)

    fun repeatSummary(): String = when (dayMode) {
        DayMode.ONCE -> oneTimeDate?.let { "${it.monthValue}月${it.dayOfMonth}日 · 仅一次" } ?: "仅一次"
        DayMode.WORKDAY -> "法定工作日"
        DayMode.EVERY_DAY -> "每天"
        DayMode.REST_DAY -> "休息日"
        DayMode.CUSTOM -> {
            if (customDays.isEmpty()) {
                "自定义（未选星期）"
            } else {
                val names = listOf("一", "二", "三", "四", "五", "六", "日")
                val text = customDays.sorted().joinToString("") { names[it - 1] }
                "每周$text" + if (observeHolidays) " · 跟随调休" else ""
            }
        }
    }
}

data class AlarmPlan(
    val alarm: Alarm,
    val dayInfo: DayInfo,
    val base: LocalDateTime,
    val adjusted: LocalDateTime,
    val offsetMinutes: Int,
    val reasons: List<String>,
    val weather: WeatherPoint?,
    val weatherAvailable: Boolean,
) {
    val date: LocalDate get() = adjusted.toLocalDate()
    val timeText: String get() = "%02d:%02d".format(adjusted.hour, adjusted.minute)
    val baseTimeText: String get() = "%02d:%02d".format(base.hour, base.minute)
    val offsetLabel: String
        get() = when {
            offsetMinutes == 0 -> "按基准时间"
            offsetMinutes < 0 -> "提前 ${-offsetMinutes} 分钟"
            else -> "延后 $offsetMinutes 分钟"
        }
    val weatherLine: String
        get() = weather?.let {
            "${it.emoji} ${it.codeLabel} ${it.tempMin?.let { min -> "%.0f".format(min) } ?: "-"}° ~ " +
                "${it.tempMax?.let { max -> "%.0f".format(max) } ?: "-"}° · 降水 ${it.precipProbability?.let { p -> "%.0f".format(p) } ?: "-"}%"
        } ?: "暂无天气数据"
}

data class CityOption(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String? = null,
    val country: String? = null,
    val timezone: String? = null,
) {
    val displayName: String
        get() = listOfNotNull(name, admin1, country).distinct().joinToString(" · ")
}

/** 通勤方式。不同方式走不同的高德路径接口，耗时不能混用。 */
@Serializable
enum class CommuteMode(
    val label: String,
    val hint: String,
    /** 路径接口原始耗时到实际耗时的折算系数；电瓶车按骑行路线折算。 */
    val timeFactor: Double = 1.0,
) {
    WALKING("步行", "步行路线", 1.0),
    BICYCLING("自行车", "骑行路线", 1.0),
    EBIKE("电瓶车", "骑行路线 × 0.72", 0.72),
    DRIVING("开车", "实时路况", 1.0),
    TRANSIT("公共交通", "含换乘接驳", 1.0);

    fun estimateMinutes(rawSeconds: Int): Int {
        if (rawSeconds <= 0) return 1
        return ceil(rawSeconds * timeFactor / 60.0).toInt().coerceAtLeast(1)
    }
}

@Serializable
data class CommutePlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val cityName: String? = null,
) {
    val displayName: String get() = listOfNotNull(name, address?.takeIf { it.isNotBlank() }).joinToString(" · ")
    val coordinate: String get() = "%.6f,%.6f".format(longitude, latitude)
}

@Serializable
data class CommutePlan(
    val enabled: Boolean = false,
    val mode: CommuteMode = CommuteMode.DRIVING,
    val origin: CommutePlace? = null,
    val destination: CommutePlace? = null,
    val arrivalHour: Int = 9,
    val arrivalMinute: Int = 0,
    val bufferMinutes: Int = 10,
    val workdaysOnly: Boolean = true,
    val routeMinutes: Int? = null,
    val routeDistanceMeters: Int? = null,
    val routeUpdatedAt: Long = 0L,
    val routeSummary: String? = null,
) {
    val arrivalText: String get() = "%02d:%02d".format(arrivalHour, arrivalMinute)
    val isConfigured: Boolean get() = origin != null && destination != null
}

data class CommutePreview(
    val plan: CommutePlan,
    val arrival: LocalDateTime,
    val latestDeparture: LocalDateTime,
    val remindAt: LocalDateTime,
    val routeMinutes: Int,
) {
    val arrivalText: String get() = "%02d:%02d".format(arrival.hour, arrival.minute)
    val latestDepartureText: String get() = "%02d:%02d".format(latestDeparture.hour, latestDeparture.minute)
    val remindText: String get() = "%02d:%02d".format(remindAt.hour, remindAt.minute)
}