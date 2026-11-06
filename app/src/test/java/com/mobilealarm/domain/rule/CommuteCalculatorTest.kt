package com.mobilealarm.domain.rule

import com.mobilealarm.domain.holiday.HolidayJsonParser
import com.mobilealarm.domain.holiday.WorkdayCalendar
import com.mobilealarm.domain.model.CommutePlan
import com.mobilealarm.domain.model.CommutePlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CommuteCalculatorTest {

    private val calendar = WorkdayCalendar(
        HolidayJsonParser.parseBundled(
            """
            {"years":{"2025":{
              "holidays":{"2025-01-28":"春节","2025-01-29":"春节","2025-01-30":"春节","2025-01-31":"春节","2025-02-03":"春节","2025-02-04":"春节"},
              "workdays":{"2025-01-26":"调休上班","2025-02-08":"调休上班"}
            }}}
            """.trimIndent(),
        ),
    )

    private val place = CommutePlace("公司", 30.0, 120.0)

    @Test
    fun `arrival minus route and buffer gives reminder time`() {
        val plan = basePlan(routeMinutes = 35, bufferMinutes = 10)
        val preview = CommuteCalculator.preview(
            plan,
            LocalDateTime.of(2025, 1, 27, 7, 0),
            calendar,
        )

        assertNotNull(preview)
        assertEquals(LocalDateTime.of(2025, 1, 27, 8, 25), preview!!.latestDeparture)
        assertEquals(LocalDateTime.of(2025, 1, 27, 8, 15), preview.remindAt)
        assertEquals("08:15", preview.remindText)
        assertEquals("09:00", preview.arrivalText)
    }

    @Test
    fun `past reminder rolls to the next statutory workday`() {
        val plan = basePlan(routeMinutes = 35, bufferMinutes = 10)
        // 2025-01-28 is Spring Festival; 01-29..02-04 are holidays except 02-08 makeup.
        val preview = CommuteCalculator.preview(
            plan,
            LocalDateTime.of(2025, 1, 28, 8, 0),
            calendar,
        )

        assertNotNull(preview)
        assertEquals(LocalDate.of(2025, 2, 5), preview!!.arrival.toLocalDate())
    }

    @Test
    fun `makeup Sunday counts as a workday`() {
        val plan = basePlan(routeMinutes = 20, bufferMinutes = 5)
        val preview = CommuteCalculator.preview(
            plan,
            LocalDateTime.of(2025, 1, 25, 10, 0),
            calendar,
        )

        assertNotNull(preview)
        assertEquals(LocalDate.of(2025, 1, 26), preview!!.arrival.toLocalDate())
    }

    @Test
    fun `workdays only off allows weekend`() {
        val plan = basePlan(routeMinutes = 20, bufferMinutes = 0).copy(workdaysOnly = false)
        val preview = CommuteCalculator.preview(
            plan,
            LocalDateTime.of(2025, 1, 25, 7, 0),
            calendar,
        )

        assertNotNull(preview)
        assertEquals(LocalDate.of(2025, 1, 25), preview!!.arrival.toLocalDate())
    }

    @Test
    fun `missing route or places produces no preview`() {
        val noRoute = basePlan(routeMinutes = null, bufferMinutes = 10)
        val noPlace = basePlan(routeMinutes = 30, bufferMinutes = 0).copy(origin = null)

        assertNull(CommuteCalculator.preview(noRoute, LocalDateTime.of(2025, 1, 27, 7, 0), calendar))
        assertNull(CommuteCalculator.preview(noPlace, LocalDateTime.of(2025, 1, 27, 7, 0), calendar))
    }

    @Test
    fun `negative buffer is treated as zero`() {
        val plan = basePlan(routeMinutes = 30, bufferMinutes = -15)
        val preview = CommuteCalculator.preview(
            plan,
            LocalDateTime.of(2025, 1, 27, 7, 0),
            calendar,
        )

        assertNotNull(preview)
        assertEquals(preview!!.latestDeparture, preview.remindAt)
    }

    private fun basePlan(routeMinutes: Int?, bufferMinutes: Int) = CommutePlan(
        enabled = true,
        origin = place,
        destination = place.copy(name = "家"),
        arrivalHour = 9,
        arrivalMinute = 0,
        bufferMinutes = bufferMinutes,
        workdaysOnly = true,
        routeMinutes = routeMinutes,
        routeDistanceMeters = 12_000,
        routeUpdatedAt = 1L,
    )
}