package com.mobilealarm.domain.rule

import com.mobilealarm.domain.holiday.HolidayJsonParser
import com.mobilealarm.domain.holiday.WorkdayCalendar
import com.mobilealarm.domain.model.Alarm
import com.mobilealarm.domain.model.DayKind
import com.mobilealarm.domain.model.DayMode
import com.mobilealarm.domain.model.RuleComparator
import com.mobilealarm.domain.model.WeatherBundle
import com.mobilealarm.domain.model.WeatherDay
import com.mobilealarm.domain.model.WeatherGroup
import com.mobilealarm.domain.model.WeatherHour
import com.mobilealarm.domain.model.WeatherMetric
import com.mobilealarm.domain.model.WeatherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AlarmTimeCalculatorTest {

    private val calendar = calendar2025()

    @Test
    fun `workday alarm skips weekend and holiday but includes makeup day`() {
        val alarm = Alarm(hour = 8, minute = 0, dayMode = DayMode.WORKDAY)
        val saturday = LocalDate.of(2025, 1, 25)
        val makeupSunday = LocalDate.of(2025, 1, 26)
        val springFestival = LocalDate.of(2025, 1, 28)
        val normalMonday = LocalDate.of(2025, 2, 10)

        assertFalse(AlarmTimeCalculator.isActive(alarm, saturday, calendar))
        assertTrue(AlarmTimeCalculator.isActive(alarm, makeupSunday, calendar))
        assertFalse(AlarmTimeCalculator.isActive(alarm, springFestival, calendar))
        assertTrue(AlarmTimeCalculator.isActive(alarm, normalMonday, calendar))
        assertEquals(DayKind.MAKEUP, calendar.info(makeupSunday).kind)
        assertEquals(DayKind.HOLIDAY, calendar.info(springFestival).kind)
    }

    @Test
    fun `rest day alarm rings on holiday and weekend`() {
        val alarm = Alarm(hour = 9, minute = 30, dayMode = DayMode.REST_DAY)
        assertTrue(AlarmTimeCalculator.isActive(alarm, LocalDate.of(2025, 1, 28), calendar))
        assertTrue(AlarmTimeCalculator.isActive(alarm, LocalDate.of(2025, 1, 25), calendar))
        assertFalse(AlarmTimeCalculator.isActive(alarm, LocalDate.of(2025, 2, 10), calendar))
    }

    @Test
    fun `custom weekday follows official holiday switch`() {
        val alarm = Alarm(
            dayMode = DayMode.CUSTOM,
            customDays = setOf(7),
            observeHolidays = true,
        )
        assertTrue(AlarmTimeCalculator.isActive(alarm, LocalDate.of(2025, 1, 26), calendar))
        assertTrue(AlarmTimeCalculator.isActive(alarm, LocalDate.of(2025, 2, 9), calendar))
        assertFalse(AlarmTimeCalculator.isActive(alarm, LocalDate.of(2025, 2, 2), calendar))
    }

    @Test
    fun `weather rules sum offsets and clamp to three hours`() {
        val alarm = Alarm(
            hour = 8,
            minute = 0,
            weatherRules = listOf(
                WeatherRule(
                    metric = WeatherMetric.WEATHER_GROUP,
                    comparator = RuleComparator.IN,
                    groups = listOf(WeatherGroup.RAIN),
                    offsetMinutes = -20,
                ),
                WeatherRule(
                    metric = WeatherMetric.PRECIP_PROB,
                    comparator = RuleComparator.GTE,
                    threshold = 60.0,
                    offsetMinutes = -10,
                ),
                WeatherRule(
                    metric = WeatherMetric.TEMPERATURE,
                    comparator = RuleComparator.LTE,
                    threshold = 10.0,
                    offsetMinutes = -15,
                ),
            ),
        )
        val plan = AlarmTimeCalculator.planFor(alarm, LocalDate.of(2025, 2, 10), calendar, rainyWeather())
        assertNotNull(plan)
        assertEquals(7, plan!!.adjusted.hour)
        assertEquals(15, plan.adjusted.minute)
        assertEquals(-45, plan.offsetMinutes)
        assertEquals(3, plan.reasons.size)

        val clamped = AlarmTimeCalculator.planFor(
            alarm.copy(weatherRules = listOf(
                WeatherRule(metric = WeatherMetric.WEATHER_GROUP, comparator = RuleComparator.IN, groups = listOf(WeatherGroup.RAIN), offsetMinutes = 120),
                WeatherRule(metric = WeatherMetric.PRECIP_PROB, comparator = RuleComparator.GTE, threshold = 50.0, offsetMinutes = 120),
            )),
            LocalDate.of(2025, 2, 10),
            calendar,
            rainyWeather(),
        )
        assertEquals(180, clamped!!.offsetMinutes)
        assertEquals(11, clamped.adjusted.hour)
    }

    @Test
    fun `no weather forecast leaves base time untouched`() {
        val alarm = Alarm(hour = 7, minute = 45, weatherRules = listOf(WeatherRule(offsetMinutes = -30)))
        val plan = AlarmTimeCalculator.planFor(alarm, LocalDate.of(2025, 2, 10), calendar, null)
        assertNotNull(plan)
        assertEquals(7, plan!!.adjusted.hour)
        assertEquals(45, plan.adjusted.minute)
        assertEquals(0, plan.offsetMinutes)
        assertFalse(plan.weatherAvailable)
    }

    @Test
    fun `nextPlan searches past today's missed time`() {
        val alarm = Alarm(hour = 8, minute = 0, dayMode = DayMode.WORKDAY)
        val plan = AlarmTimeCalculator.nextPlan(
            alarm,
            LocalDateTime.of(2025, 2, 10, 9, 0),
            calendar,
            null,
        )
        assertEquals(LocalDate.of(2025, 2, 11), plan!!.adjusted.toLocalDate())
    }

    @Test
    fun `one time alarm is active only on its selected date`() {
        val alarm = Alarm(
            hour = 8,
            minute = 0,
            dayMode = DayMode.ONCE,
            oneTimeDate = LocalDate.of(2025, 2, 10),
        )
        assertTrue(AlarmTimeCalculator.isActive(alarm, LocalDate.of(2025, 2, 10), calendar))
        assertFalse(AlarmTimeCalculator.isActive(alarm, LocalDate.of(2025, 2, 11), calendar))
        assertFalse(AlarmTimeCalculator.isActive(alarm.copy(oneTimeDate = null), LocalDate.of(2025, 2, 10), calendar))
    }

    @Test
    fun `one time next plan works beyond the normal search horizon`() {
        val target = LocalDate.of(2025, 6, 1)
        val alarm = Alarm(hour = 8, minute = 0, dayMode = DayMode.ONCE, oneTimeDate = target)
        val plan = AlarmTimeCalculator.nextPlan(
            alarm,
            LocalDateTime.of(2025, 2, 10, 9, 0),
            calendar,
            null,
        )
        assertNotNull(plan)
        assertEquals(target, plan!!.adjusted.toLocalDate())
        assertEquals(8, plan.adjusted.hour)
    }

    @Test
    fun `one time alarm in the past has no next plan`() {
        val alarm = Alarm(
            hour = 8,
            minute = 0,
            dayMode = DayMode.ONCE,
            oneTimeDate = LocalDate.of(2025, 2, 9),
        )
        assertNull(
            AlarmTimeCalculator.nextPlan(
                alarm,
                LocalDateTime.of(2025, 2, 10, 9, 0),
                calendar,
                null,
            ),
        )
    }

    @Test
    fun `override turns a holiday into a workday`() {
        val date = LocalDate.of(2025, 1, 28)
        val overridden = WorkdayCalendar(
            yearData = calendar2025Data(),
            overrides = mapOf(date to true),
        )
        assertTrue(overridden.isWorkday(date))
        assertEquals(DayKind.OVERRIDE_WORK, overridden.info(date).kind)
        assertNull(
            AlarmTimeCalculator.planFor(
                Alarm(dayMode = DayMode.REST_DAY),
                date,
                overridden,
                null,
            ),
        )
    }

    private fun calendar2025(): WorkdayCalendar = WorkdayCalendar(calendar2025Data())

    private fun calendar2025Data() = HolidayJsonParser.parseBundled(
        """
        {"years":{"2025":{
          "holidays":{"2025-01-28":"春节","2025-01-29":"春节","2025-02-02":"春节","2025-02-03":"春节","2025-02-04":"春节"},
          "workdays":{"2025-01-26":"调休上班","2025-02-08":"调休上班"}
        }}}
        """.trimIndent(),
    )

    private fun rainyWeather(): WeatherBundle = WeatherBundle(
        city = "测试",
        latitude = 30.0,
        longitude = 120.0,
        timezone = "Asia/Shanghai",
        fetchedAt = 0L,
        days = listOf(
            WeatherDay(
                date = LocalDate.of(2025, 2, 10),
                code = 63,
                tempMax = 12.0,
                tempMin = 6.0,
                precipProbMax = 80.0,
                precipSum = 12.0,
                windMax = 18.0,
                uvMax = 2.0,
                hourly = mapOf(
                    8 to WeatherHour(code = 63, temperature = 8.0, precipProbability = 75.0, windSpeed = 15.0),
                ),
            ),
        ),
    )
}