package com.arnau.usagestats.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

data class UsageOverview(
    val unlocksToday: Int,
    val avgUnlocksLast7Days: Double,
    val usageMillisToday: Long,
    val avgUsageMillisLast7Days: Double
)

/**
 * Las medias de "últimos 7 días" cubren los 7 días naturales anteriores a hoy
 * (de hace 7 días hasta ayer, ambos incluidos), nunca el día de hoy a medias.
 */
object UsageStatsCalculator {

    fun calculate(context: Context): UsageOverview {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val todayStart = startOfDay(Calendar.getInstance()).timeInMillis
        val now = System.currentTimeMillis()

        val unlocksToday = countUnlocks(usm, todayStart, now)
        val usageToday = sumUsageForRange(usm, todayStart, now)

        val sevenDaysAgoStart = startOfDay(daysAgo(7)).timeInMillis
        // Rango [hace 7 días 00:00, hoy 00:00) = los 7 días naturales anteriores a hoy.
        val unlocksByDay = countUnlocksPerDay(usm, sevenDaysAgoStart, todayStart)
        val avgUnlocks = unlocksByDay.values.sum().toDouble() / 7.0

        var totalUsageLast7Days = 0L
        for (daysBack in 1..7) {
            val dayStart = startOfDay(daysAgo(daysBack))
            val dayEnd = (dayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            totalUsageLast7Days += sumUsageForRange(usm, dayStart.timeInMillis, dayEnd.timeInMillis)
        }
        val avgUsage = totalUsageLast7Days.toDouble() / 7.0

        return UsageOverview(
            unlocksToday = unlocksToday,
            avgUnlocksLast7Days = avgUnlocks,
            usageMillisToday = usageToday,
            avgUsageMillisLast7Days = avgUsage
        )
    }

    private fun daysAgo(days: Int): Calendar =
        (Calendar.getInstance().clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -days) }

    private fun startOfDay(cal: Calendar): Calendar {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c
    }

    private fun sumUsageForRange(usm: UsageStatsManager, start: Long, end: Long): Long {
        if (end <= start) return 0L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: return 0L
        return stats.sumOf { it.totalTimeInForeground }
    }

    private fun countUnlocks(usm: UsageStatsManager, start: Long, end: Long): Int {
        if (end <= start) return 0
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        var count = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) count++
        }
        return count
    }

    /** Cuenta los KEYGUARD_HIDDEN de [start, end) agrupados por día de calendario del evento. */
    private fun countUnlocksPerDay(usm: UsageStatsManager, start: Long, end: Long): Map<Long, Int> {
        val result = mutableMapOf<Long, Int>()
        if (end <= start) return result
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        val bucketCal = Calendar.getInstance()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.KEYGUARD_HIDDEN) continue
            bucketCal.timeInMillis = event.timeStamp
            val dayKey = startOfDay(bucketCal).timeInMillis
            result[dayKey] = (result[dayKey] ?: 0) + 1
        }
        return result
    }
}
