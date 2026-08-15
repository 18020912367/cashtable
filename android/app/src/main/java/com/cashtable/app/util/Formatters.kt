package com.cashtable.app.util

import java.text.DecimalFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 金额与日期显示格式化。 */
object Formatters {

    private val moneyFmt = DecimalFormat("#,##0.00")

    /** 金额绝对值千分位，如 1234.5 -> "1,234.50" */
    fun money(amount: Double): String = moneyFmt.format(Math.abs(amount))

    /** "2026-08" -> "2026年8月" */
    fun monthLabel(month: String): String {
        val parts = month.split("-")
        return if (parts.size == 2) "${parts[0]}年${parts[1].toInt()}月" else month
    }

    /** "2026-08-07" -> "8月7日 周五" */
    fun dayLabel(date: String): String = try {
        val d = LocalDate.parse(date)
        val week = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[d.dayOfWeek.value - 1]
        "${d.monthValue}月${d.dayOfMonth}日 $week"
    } catch (_: Exception) {
        date
    }

    /** "2026-08-07 10:30" -> "10:30" */
    fun timeLabel(dt: String): String = dt.substringAfter(' ').take(5)

    /** 当前时间 "yyyy-MM-dd HH:mm" */
    fun nowDateTime(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    /** "yyyy-MM-dd HH:mm" -> LocalDateTime */
    fun parseDateTime(s: String): LocalDateTime? = try {
        LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    } catch (_: Exception) {
        null
    }

    fun formatDateTime(dt: LocalDateTime): String = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
