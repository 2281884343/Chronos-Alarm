package com.mobilealarm.data.local

import com.mobilealarm.domain.model.Alarm
import com.mobilealarm.domain.model.DayMode
import com.mobilealarm.domain.model.RuleComparator
import com.mobilealarm.domain.model.WeatherGroup
import com.mobilealarm.domain.model.WeatherMetric
import com.mobilealarm.domain.model.WeatherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AlarmMappersTest {

    @Test
    fun `round trips weather rules and custom weekdays`() {
        val alarm = Alarm(
            id = 7,
            label = "上班",
            hour = 8,
            minute = 5,
            dayMode = DayMode.CUSTOM,
            customDays = setOf(1, 3, 5),
            observeHolidays = false,
            weatherRules = listOf(
                WeatherRule(
                    metric = WeatherMetric.WEATHER_GROUP,
                    comparator = RuleComparator.IN,
                    groups = listOf(WeatherGroup.RAIN, WeatherGroup.SNOW),
                    offsetMinutes = -30,
                ),
                WeatherRule(
                    metric = WeatherMetric.TEMP_MIN,
                    comparator = RuleComparator.LTE,
                    threshold = 0.0,
                    offsetMinutes = 20,
                ),
            ),
            vibrate = false,
            snoozeMinutes = 5,
            ringtone = "content://ringtone/1",
        )

        val restored = alarm.toEntity().toDomain()
        assertEquals(alarm, restored)
    }

    @Test
    fun `round trips a one time date`() {
        val alarm = Alarm(
            id = 9,
            hour = 6,
            minute = 40,
            dayMode = DayMode.ONCE,
            oneTimeDate = LocalDate.of(2026, 11, 8),
            observeHolidays = false,
        )
        assertEquals(alarm, alarm.toEntity().toDomain())
    }

    @Test
    fun `bad rules or unknown day mode do not crash`() {
        val entity = AlarmEntity(
            id = 1,
            dayMode = "NOT_A_MODE",
            customDays = "0,2,x,9",
            weatherRulesJson = "{broken",
            hour = 99,
            minute = -3,
            snoozeMinutes = 0,
        )
        val alarm = entity.toDomain()
        assertEquals(DayMode.WORKDAY, alarm.dayMode)
        assertEquals(setOf(2), alarm.customDays)
        assertTrue(alarm.weatherRules.isEmpty())
        assertEquals(23, alarm.hour)
        assertEquals(0, alarm.minute)
        assertEquals(1, alarm.snoozeMinutes)
    }
}