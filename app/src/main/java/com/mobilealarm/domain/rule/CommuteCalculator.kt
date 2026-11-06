package com.mobilealarm.domain.rule

import com.mobilealarm.domain.holiday.WorkdayCalendar
import com.mobilealarm.domain.model.CommutePlan
import com.mobilealarm.domain.model.CommutePreview
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 通勤提醒的核心推导：
 *
 * 到达时间 - 所选出行方式耗时 = 最迟出门时间
 * 最迟出门时间 - 用户 buffer = 实际提醒时间
 *
 * 只扫描未来 14 天，避免节假日数据缺失时排到很远。
 */
object CommuteCalculator {
    fun preview(
        plan: CommutePlan,
        now: LocalDateTime,
        calendar: WorkdayCalendar,
    ): CommutePreview? {
        val routeMinutes = plan.routeMinutes?.takeIf { it > 0 } ?: return null
        if (!plan.isConfigured) return null
        val arrivalTime = LocalTime.of(
            plan.arrivalHour.coerceIn(0, 23),
            plan.arrivalMinute.coerceIn(0, 59),
        )
        for (offset in 0..14) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (plan.workdaysOnly && !calendar.info(date).isWorkday) continue
            val arrival = LocalDateTime.of(date, arrivalTime)
            val latestDeparture = arrival.minusMinutes(routeMinutes.toLong())
            val remindAt = latestDeparture.minusMinutes(plan.bufferMinutes.coerceAtLeast(0).toLong())
            if (remindAt.isAfter(now)) {
                return CommutePreview(
                    plan = plan,
                    arrival = arrival,
                    latestDeparture = latestDeparture,
                    remindAt = remindAt,
                    routeMinutes = routeMinutes,
                )
            }
        }
        return null
    }

    fun nextDate(plan: CommutePlan, from: LocalDate, calendar: WorkdayCalendar): LocalDate? {
        for (offset in 0..14) {
            val date = from.plusDays(offset.toLong())
            if (plan.workdaysOnly && !calendar.info(date).isWorkday) continue
            return date
        }
        return null
    }
}