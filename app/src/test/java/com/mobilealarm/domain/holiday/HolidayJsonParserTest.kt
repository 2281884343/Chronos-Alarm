package com.mobilealarm.domain.holiday

import com.mobilealarm.domain.model.DayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayJsonParserTest {

    @Test
    fun `parses bundled schema with holiday and makeup maps`() {
        val years = HolidayJsonParser.parseBundled(
            """
            {"_meta":{"note":"test"},"years":{"2026":{
              "holidays":{"2026-01-01":"元旦","2026-02-17":"春节"},
              "workdays":{"2026-01-04":"调休上班"}
            }}}
            """.trimIndent(),
        )
        assertEquals(listOf(2026), years.keys.toList())
        val data = years.getValue(2026)
        assertEquals("元旦", data.records.getValue(LocalDate.of(2026, 1, 1)).name)
        assertEquals(DayKind.HOLIDAY, data.records.getValue(LocalDate.of(2026, 2, 17)).kind)
        assertEquals(DayKind.MAKEUP, data.records.getValue(LocalDate.of(2026, 1, 4)).kind)
        assertTrue(data.records.getValue(LocalDate.of(2026, 1, 4)).isWorkday)
    }

    @Test
    fun `parses holiday-cn schema`() {
        val data = HolidayJsonParser.parseHolidayCn(
            """
            {"year":2025,"papers":[],"days":[
              {"name":"元旦","date":"2025-01-01","isOffDay":true},
              {"name":"春节","date":"2025-01-28","isOffDay":true},
              {"date":"2025-01-26","isOffDay":false}
            ]}
            """.trimIndent(),
            expectedYear = 2025,
            source = "在线",
        )
        assertNotNull(data)
        assertEquals(2025, data!!.year)
        assertEquals(3, data.records.size)
        assertEquals(DayKind.HOLIDAY, data.records.getValue(LocalDate.of(2025, 1, 28)).kind)
        assertEquals(DayKind.MAKEUP, data.records.getValue(LocalDate.of(2025, 1, 26)).kind)
        assertEquals("调休上班", data.records.getValue(LocalDate.of(2025, 1, 26)).name)
    }

    @Test
    fun `empty online document is rejected so bundled data is not overwritten`() {
        assertNull(
            HolidayJsonParser.parseHolidayCn(
                """{"year":2027,"papers":[],"days":[]}""",
                expectedYear = 2027,
                source = "在线",
            ),
        )
    }

    @Test
    fun `calendar falls back to weekday outside covered years`() {
        val calendar = WorkdayCalendar()
        val monday = LocalDate.of(2030, 1, 7)
        val sunday = LocalDate.of(2030, 1, 6)
        assertTrue(calendar.isWorkday(monday))
        assertFalse(calendar.isWorkday(sunday))
        assertEquals(DayKind.NORMAL, calendar.info(monday).kind)
        assertEquals(DayKind.WEEKEND, calendar.info(sunday).kind)
        assertFalse(calendar.info(monday).covered)
    }
}