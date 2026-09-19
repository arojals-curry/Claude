package com.arnau.usagestats.data.mood

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.arnau.usagestats.data.DateUtils
import com.arnau.usagestats.data.db.AppDatabase
import com.arnau.usagestats.data.db.AppSessionDao
import com.arnau.usagestats.data.db.AppSessionEntity
import java.util.concurrent.TimeUnit

/**
 * Reconstruye app_sessions emparejando MOVE_TO_FOREGROUND/MOVE_TO_BACKGROUND
 * por packageName. Es la base para calcular tiempo acumulado e inactividad
 * con precisión (queryUsageStats no da eso por sí solo).
 */
object AppSessionSyncManager {

    // Margen de seguridad si nunca se ha sincronizado nada: no intentar leer
    // más atrás de esto en la primera ejecución.
    private val MAX_LOOKBACK_MS = TimeUnit.DAYS.toMillis(14)

    suspend fun sync(context: Context, db: AppDatabase, upToMillis: Long = System.currentTimeMillis()) {
        val dao = db.appSessionDao()
        val boundary = dao.getLatestBoundary()
        val start = maxOf(boundary ?: (upToMillis - MAX_LOOKBACK_MS), upToMillis - MAX_LOOKBACK_MS)
        if (upToMillis <= start) return

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(start, upToMillis)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> handleForeground(dao, event.packageName, event.timeStamp)
                UsageEvents.Event.MOVE_TO_BACKGROUND -> handleBackground(dao, event.packageName, event.timeStamp)
            }
        }
    }

    private suspend fun handleForeground(dao: AppSessionDao, packageName: String, timestamp: Long) {
        if (dao.getOpenSession(packageName) != null) return // ya había una sesión abierta, ignoramos el duplicado
        dao.insert(
            AppSessionEntity(
                packageName = packageName,
                date = DateUtils.dateKey(timestamp),
                startedAt = timestamp,
                endedAt = null,
                durationMs = null
            )
        )
    }

    private suspend fun handleBackground(dao: AppSessionDao, packageName: String, timestamp: Long) {
        val open = dao.getOpenSession(packageName) ?: return // BACKGROUND sin FOREGROUND previo conocido: se ignora
        if (timestamp <= open.startedAt) return
        dao.update(open.copy(endedAt = timestamp, durationMs = timestamp - open.startedAt))
    }
}
