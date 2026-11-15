package com.example.data.local.db

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object HabitDateUtils {
    private const val ISO_PATTERN = "yyyy-MM-dd"

    fun todayIso(): String = formatIso(Date())

    fun yesterdayIso(): String = offsetIso(today = Date(), daysOffset = -1)

    fun offsetIso(today: Date = Date(), daysOffset: Int): String {
        val calendar = Calendar.getInstance()
        calendar.time = today
        calendar.add(Calendar.DAY_OF_YEAR, daysOffset)
        return formatIso(calendar.time)
    }

    fun parseIso(isoDate: String): Date? = try {
        SimpleDateFormat(ISO_PATTERN, Locale.US).parse(isoDate)
    } catch (_: Exception) {
        null
    }

    fun formatIso(date: Date): String =
        SimpleDateFormat(ISO_PATTERN, Locale.US).format(date)

    fun daysBetween(earlierIso: String, laterIso: String): Long {
        val d1 = parseIso(earlierIso) ?: return Long.MAX_VALUE
        val d2 = parseIso(laterIso) ?: return Long.MAX_VALUE
        val diffMs = d2.time - d1.time
        return TimeUnit.MILLISECONDS.toDays(diffMs)
    }

    fun getPastNDays(count: Int, includeToday: Boolean = true): List<String> {
        val list = mutableListOf<String>()
        val startOffset = if (includeToday) 0 else -1
        for (i in (count - 1) downTo 0) {
            val offset = startOffset - i
            list.add(offsetIso(Date(), offset))
        }
        return list
    }

    fun formatDisplayDate(isoDate: String): String {
        val today = todayIso()
        val yesterday = yesterdayIso()
        if (isoDate == today) return "Today"
        if (isoDate == yesterday) return "Yesterday"
        val d = parseIso(isoDate) ?: return isoDate
        return SimpleDateFormat("EEE, MMM d", Locale.US).format(d)
    }

    fun formatShortDayOfWeek(isoDate: String): String {
        val d = parseIso(isoDate) ?: return ""
        return SimpleDateFormat("EEE", Locale.US).format(d)
    }
}
