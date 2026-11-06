package com.mobilealarm.domain.rule

import com.mobilealarm.domain.holiday.WorkdayCalendar
import com.mobilealarm.domain.model.Alarm
import com.mobilealarm.domain.model.AlarmPlan
import com.mobilealarm.domain.model.DayKind
import com.mobilealarm.domain.model.DayMode
import com.mobilealarm.domain.model.RuleComparator
import com.mobilealarm.domain.model.WeatherBundle
import com.mobilealarm.domain.model.WeatherMetric
import com.mobilealarm.domain.model.WeatherPoint
import com.mobilealarm.domain.model.WeatherRule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 把「基准时间 + 日期规则 + 天气条件」换算成一次具体的响铃计划。
 *
 * 设计约定：
 * - 天气条件按“闹钟当天、闹钟基准时刻”的预报判断；
 * - 多个命中的条件叠加调整，总幅度限制在 ±180 分钟；
 * - 调整后的时间不跨天（最早 00:00，最晚 23:59），避免日期语义混乱；
 * - 没有该日期的预报时不做任何调整，按基准时间响铃。
 */
object AlarmTimeCalculator {
    const val MAX_OFFSET_MINUTES = 180
    const val DEFAULT_HORIZON_DAYS = 32

    fun isActive(alarm: Alarm, date: LocalDate, calendar: WorkdayCalendar): Boolean {
        val info = calendar.info(date)
        return when (alarm.dayMode) {
            DayMode.ONCE -> alarm.oneTimeDate == date
            DayMode.WORKDAY -> info.isWorkday
            DayMode.REST_DAY -> !info.isWorkday
            DayMode.EVERY_DAY -> true
            DayMode.CUSTOM -> {
                if (alarm.customDays.isEmpty()) return false
                if (!alarm.observeHolidays) {
                    date.dayOfWeek.value in alarm.customDays
                } else {
                    when (info.kind) {
                        DayKind.HOLIDAY, DayKind.OVERRIDE_REST -> false
                        DayKind.MAKEUP, DayKind.OVERRIDE_WORK -> true
                        else -> date.dayOfWeek.value in alarm.customDays
                    }
                }
            }
        }
    }

    fun ruleMatches(rule: WeatherRule, point: WeatherPoint): Boolean {
        return if (rule.metric.isGroup) {
            val group = point.group ?: return false
            when (rule.comparator) {
                RuleComparator.IN -> group in rule.groups
                RuleComparator.NOT_IN -> group !in rule.groups
                else -> false
            }
        } else {
            val value = point.valueOf(rule.metric) ?: return false
            when (rule.comparator) {
                RuleComparator.GTE -> value >= rule.threshold
                RuleComparator.LTE -> value <= rule.threshold
                else -> false
            }
        }
    }

    private fun actualText(rule: WeatherRule, point: WeatherPoint): String {
        return if (rule.metric.isGroup) {
            point.group?.label ?: "未知"
        } else {
            val raw = point.valueOf(rule.metric) ?: return "未知"
            val value = if (raw % 1.0 == 0.0) raw.toInt().toString() else "%.1f".format(raw)
            "$value${rule.metric.unit}"
        }
    }

    fun calculateOffset(alarm: Alarm, point: WeatherPoint?): Pair<Int, List<String>> {
        if (point == null) return 0 to emptyList()
        var total = 0
        val reasons = mutableListOf<String>()
        for (rule in alarm.weatherRules) {
            if (!rule.enabled) continue
            if (!ruleMatches(rule, point)) continue
            total += rule.offsetMinutes
            reasons += "${rule.conditionText()}（实际 ${actualText(rule, point)}）→ ${rule.offsetLabel}"
        }
        if (total == 0) return 0 to reasons
        val clamped = total.coerceIn(-MAX_OFFSET_MINUTES, MAX_OFFSET_MINUTES)
        if (clamped != total) {
            reasons += "调整幅度超过 ±$MAX_OFFSET_MINUTES 分钟，已截断"
        }
        return clamped to reasons
    }

    fun planFor(
        alarm: Alarm,
        date: LocalDate,
        calendar: WorkdayCalendar,
        weather: WeatherBundle?,
    ): AlarmPlan? {
        if (!isActive(alarm, date, calendar)) return null
        val dayInfo = calendar.info(date)
        val base = LocalDateTime.of(date, LocalTime.of(alarm.hour, alarm.minute))
        val weatherPoint = weather?.pointFor(date, alarm.hour)
        val (rawOffset, calculatedReasons) = calculateOffset(alarm, weatherPoint)
        val reasons = calculatedReasons.toMutableList()

        var adjusted = base.plusMinutes(rawOffset.toLong())
        val dayStart = date.atStartOfDay()
        val dayEnd = date.atTime(LocalTime.of(23, 59))
        if (adjusted.isBefore(dayStart)) {
            adjusted = dayStart
            reasons += "已到达当天最早时刻 00:00"
        } else if (adjusted.isAfter(dayEnd)) {
            adjusted = dayEnd
            reasons += "已到达当天最晚时刻 23:59"
        }
        val effectiveOffset = Duration.between(base, adjusted).toMinutes().toInt()

        return AlarmPlan(
            alarm = alarm,
            dayInfo = dayInfo,
            base = base,
            adjusted = adjusted,
            offsetMinutes = effectiveOffset,
            reasons = reasons,
            weather = weatherPoint,
            weatherAvailable = weatherPoint != null,
        )
    }

    fun nextPlan(
        alarm: Alarm,
        now: LocalDateTime,
        calendar: WorkdayCalendar,
        weather: WeatherBundle?,
        horizonDays: Int = DEFAULT_HORIZON_DAYS,
    ): AlarmPlan? {
        if (alarm.dayMode == DayMode.ONCE) {
            val date = alarm.oneTimeDate ?: return null
            val plan = planFor(alarm, date, calendar, weather) ?: return null
            return plan.takeIf { it.adjusted.isAfter(now) }
        }
        for (offset in 0..horizonDays) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val plan = planFor(alarm, date, calendar, weather) ?: continue
            if (plan.adjusted.isAfter(now)) return plan
        }
        return null
    }

    fun nextPlans(
        alarms: List<Alarm>,
        now: LocalDateTime,
        calendar: WorkdayCalendar,
        weather: WeatherBundle?,
        horizonDays: Int = DEFAULT_HORIZON_DAYS,
    ): Map<Long, AlarmPlan> {
        return alarms.asSequence()
            .filter { it.enabled }
            .mapNotNull { alarm -> nextPlan(alarm, now, calendar, weather, horizonDays)?.let { alarm.id to it } }
            .toMap()
    }
}