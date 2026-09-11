package com.arnau.usagestats.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.arnau.usagestats.data.db.AppDatabase
import com.arnau.usagestats.data.db.AppEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class UsageOverview(
    val unlocksToday: Int,
    val avgUnlocksLast7Days: Double,
    val usageMillisToday: Long,
    val avgUsageMillisLast7Days: Double
)

data class AppUsageDisplay(
    val packageName: String,
    val displayName: String,
    val durationMs: Long,
    val opensCount: Int
)

/**
 * Los desbloqueos se calculan siempre en caliente contra UsageEvents (no hay
 * todavía una tabla para ellos). El tiempo de uso, en cambio, se sincroniza a
 * diario en Room (ver [syncToday]) y [calculate] lee de ahí, no de
 * UsageStatsManager directamente.
 *
 * Las medias de "últimos 7 días" cubren los 7 días naturales anteriores a hoy
 * (de hace 7 días hasta ayer, ambos incluidos), nunca el día de hoy a medias.
 */
object UsageStatsCalculator {

    suspend fun calculate(context: Context, db: AppDatabase): UsageOverview {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val todayStart = startOfDay(Calendar.getInstance()).timeInMillis
        val now = System.currentTimeMillis()

        val unlocksToday = countUnlocks(usm, todayStart, now)

        val sevenDaysAgoStart = startOfDay(daysAgo(7)).timeInMillis
        val unlocksByDay = countUnlocksPerDay(usm, sevenDaysAgoStart, todayStart)
        val avgUnlocks = unlocksByDay.values.sum().toDouble() / 7.0

        val usageDailyDao = db.usageDailyDao()
        val today = dateKey(Calendar.getInstance())
        val usageToday = usageDailyDao.getForDate(today).sumOf { it.durationMs }

        val last7Dates = (1..7).map { dateKey(daysAgo(it)) }
        val avgUsage = usageDailyDao.sumDurationByPackage(last7Dates).sumOf { it.totalDuration }.toDouble() / 7.0

        return UsageOverview(
            unlocksToday = unlocksToday,
            avgUnlocksLast7Days = avgUnlocks,
            usageMillisToday = usageToday,
            avgUsageMillisLast7Days = avgUsage
        )
    }

    /** Lista de apps usadas hoy, ordenada de más a menos tiempo. Lee de Room, no recalcula nada. */
    suspend fun getTodayBreakdown(context: Context, db: AppDatabase): List<AppUsageDisplay> {
        val today = dateKey(Calendar.getInstance())
        val rows = db.usageDailyDao().getForDate(today)
        return rows.map { row ->
            val displayName = db.appDao().getByPackage(row.packageName)?.displayName ?: row.packageName
            AppUsageDisplay(
                packageName = row.packageName,
                displayName = displayName,
                durationMs = row.durationMs,
                opensCount = row.opensCount
            )
        }
    }

    /** Vuelca en Room el tiempo de uso y las aperturas de hoy, por app. Llamar al abrir la home. */
    suspend fun syncToday(context: Context, db: AppDatabase) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val todayStart = startOfDay(Calendar.getInstance()).timeInMillis
        val now = System.currentTimeMillis()
        val today = dateKey(Calendar.getInstance())

        val durations = perAppUsageForRange(usm, todayStart, now)
        val opens = perAppOpensForRange(usm, todayStart, now)
        val packages = durations.keys + opens.keys

        val appDao = db.appDao()
        val usageDailyDao = db.usageDailyDao()
        val pm = context.packageManager

        for (packageName in packages) {
            val durationMs = durations[packageName] ?: 0L
            val opensCount = opens[packageName] ?: 0
            if (durationMs <= 0L && opensCount <= 0) continue

            if (appDao.getByPackage(packageName) == null) {
                val label = try {
                    pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    packageName
                }
                appDao.upsert(AppEntity(packageName = packageName, displayName = label))
            }

            usageDailyDao.upsert(packageName, today, durationMs, opensCount)
        }
    }

    // SimpleDateFormat no es thread-safe: se crea una instancia por llamada
    // en vez de compartir un campo mutable entre corrutinas concurrentes.
    private fun dateKey(cal: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

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

    private fun perAppUsageForRange(usm: UsageStatsManager, start: Long, end: Long): Map<String, Long> {
        if (end <= start) return emptyMap()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: return emptyMap()
        return stats.groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { it.totalTimeInForeground } }
    }

    /** Cuenta MOVE_TO_FOREGROUND por paquete: cada uno es una "apertura" de esa app. */
    private fun perAppOpensForRange(usm: UsageStatsManager, start: Long, end: Long): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        if (end <= start) return result
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
            result[event.packageName] = (result[event.packageName] ?: 0) + 1
        }
        return result
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
