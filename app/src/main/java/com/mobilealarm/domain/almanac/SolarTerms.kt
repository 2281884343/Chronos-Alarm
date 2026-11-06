package com.mobilealarm.domain.almanac

import java.time.LocalDate

/**
 * 二十四节气的近似日期（2000–2099 通用的“寿星公式”简化版）。
 * 只用于首页那句“明日立冬”的历书气息，不参与任何闹钟计算。
 */
object SolarTerms {
    private data class Term(val month: Int, val day: Int, val name: String)

    // C 值取 21 世纪常用值；个别年份会差一天，因此只作为文案，不当作历法数据。
    private val terms = listOf(
        Term(1, 5, "小寒"), Term(1, 20, "大寒"),
        Term(2, 4, "立春"), Term(2, 19, "雨水"),
        Term(3, 5, "惊蛰"), Term(3, 20, "春分"),
        Term(4, 4, "清明"), Term(4, 20, "谷雨"),
        Term(5, 5, "立夏"), Term(5, 21, "小满"),
        Term(6, 5, "芒种"), Term(6, 21, "夏至"),
        Term(7, 7, "小暑"), Term(7, 23, "大暑"),
        Term(8, 7, "立秋"), Term(8, 23, "处暑"),
        Term(9, 7, "白露"), Term(9, 23, "秋分"),
        Term(10, 8, "寒露"), Term(10, 23, "霜降"),
        Term(11, 7, "立冬"), Term(11, 22, "小雪"),
        Term(12, 7, "大雪"), Term(12, 22, "冬至"),
    )

    fun termOn(date: LocalDate): String? =
        terms.firstOrNull { it.month == date.monthValue && it.day == date.dayOfMonth }?.name

    /** 今天或明天最近的节气文案，例如“明日立冬”。 */
    fun nearbyLine(today: LocalDate): String? {
        val tomorrow = today.plusDays(1)
        termOn(today)?.let { return "今日$it" }
        termOn(tomorrow)?.let { return "明日$it" }
        return null
    }
}
