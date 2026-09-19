package com.arnau.usagestats.data.mood

import android.content.Context
import com.arnau.usagestats.data.DateUtils
import com.arnau.usagestats.data.UsageStatsCalculator
import com.arnau.usagestats.data.db.AppDatabase
import com.arnau.usagestats.data.db.AppSessionDao
import com.arnau.usagestats.data.db.StateSnapshotEntity
import java.util.concurrent.TimeUnit

/**
 * Motor de backfill sin timers (TIM-23): en cada "wake" (llamar desde la
 * home, igual que ya se hacía con syncToday) reconstruye retroactivamente
 * los cortes de 30 min que se cruzaron desde el último snapshot guardado
 * (mood NULL, nadie los ve) y añade una fila final 'unlock' en vivo con el
 * mood ya calculado.
 */
object StateSnapshotSyncManager {

    private val TICK_MS = TimeUnit.MINUTES.toMillis(30)
    private const val MAX_BACKFILL_TICKS = 336 // ~7 días de margen si la app lleva mucho sin abrirse
    private val DEFAULT_DAILY_GOAL_MS = TimeUnit.HOURS.toMillis(3)

    suspend fun onWake(context: Context, db: AppDatabase, now: Long = System.currentTimeMillis()) {
        AppSessionSyncManager.sync(context, db, now)

        val snapshotDao = db.stateSnapshotDao()
        val sessionDao = db.appSessionDao()

        val lastCapturedAt = snapshotDao.getLatest()?.capturedAt
        var nextTick = if (lastCapturedAt != null) {
            DateUtils.floorToHalfHour(lastCapturedAt) + TICK_MS
        } else {
            DateUtils.floorToHalfHour(now)
        }

        val rows = mutableListOf<StateSnapshotEntity>()
        var ticks = 0
        while (nextTick < now && ticks < MAX_BACKFILL_TICKS) {
            rows += buildSnapshot(context, db, sessionDao, capturedAt = nextTick, writtenAt = now, trigger = "tick_30min")
            nextTick += TICK_MS
            ticks++
        }

        val unlockBase = buildSnapshot(context, db, sessionDao, capturedAt = now, writtenAt = now, trigger = "unlock")
        val result = MoodCalculator.moodFor(
            UsageSignal(
                cumScreenTimeMs = unlockBase.cumScreenTimeMs,
                cumUnlocksCount = unlockBase.cumUnlocksCount,
                avg7dScreenTimeMs = unlockBase.avg7dScreenTimeMs,
                avg7dUnlocks = unlockBase.avg7dUnlocks,
                idleMs = unlockBase.idleMs
            )
        )
        val unlockRow = unlockBase.copy(
            goalScreenTimeMs = result.goalScreenTimeMs,
            goalUnlocks = result.goalUnlocks,
            screenRatio = result.screenRatio,
            unlockRatio = result.unlockRatio,
            mood = result.mood.label,
            triggeredBy = result.triggeredBy.label
        )

        snapshotDao.insertAll(rows + unlockRow)
    }

    private suspend fun buildSnapshot(
        context: Context,
        db: AppDatabase,
        sessionDao: AppSessionDao,
        capturedAt: Long,
        writtenAt: Long,
        trigger: String
    ): StateSnapshotEntity {
        val date = DateUtils.dateKey(capturedAt)
        val dayStart = DateUtils.startOfDay(java.util.Calendar.getInstance().apply { timeInMillis = capturedAt }).timeInMillis

        val cumScreenTimeMs = sessionDao.cumulativeScreenTimeMs(date, capturedAt)
        val cumUnlocksCount = UsageStatsCalculator.countUnlocksInRange(context, dayStart, capturedAt)
        val idleMs = computeIdleMs(sessionDao, capturedAt)
        val (avg7dScreen, avg7dUnlocks) = averageForSameSlot(db, capturedAt)

        // goalScreenTimeMs/goalUnlocks/screenRatio/unlockRatio "provisionales": para las filas
        // tick_30min no se usan (mood queda NULL), y para la fila unlock las recalcula y
        // sobrescribe MoodCalculator justo después con la misma fórmula.
        val goalScreenTimeMs = avg7dScreen * 0.9
        val goalUnlocks = avg7dUnlocks * 0.9

        return StateSnapshotEntity(
            capturedAt = capturedAt,
            writtenAt = writtenAt,
            date = date,
            trigger = trigger,
            cumScreenTimeMs = cumScreenTimeMs,
            cumUnlocksCount = cumUnlocksCount,
            dailyGoalMs = DEFAULT_DAILY_GOAL_MS,
            avg7dScreenTimeMs = avg7dScreen,
            goalScreenTimeMs = goalScreenTimeMs,
            avg7dUnlocks = avg7dUnlocks,
            goalUnlocks = goalUnlocks,
            screenRatio = cumScreenTimeMs / maxOf(goalScreenTimeMs, 1.0),
            unlockRatio = cumUnlocksCount / maxOf(goalUnlocks, 0.5),
            idleMs = idleMs,
            mood = null,
            triggeredBy = null
        )
    }

    /**
     * ms desde la última sesión real hasta capturedAt (0 si había una sesión
     * abierta). Excluye estrictamente una sesión que arranque justo en
     * capturedAt (el bug del prototipo: con <= salía siempre 0 en la fila
     * que el usuario ve, porque el propio desbloqueo abre esa sesión).
     */
    private suspend fun computeIdleMs(sessionDao: AppSessionDao, capturedAt: Long): Long {
        val session = sessionDao.mostRecentSessionBefore(capturedAt) ?: return 0L
        val endedAt = session.endedAt
        return if (endedAt == null || endedAt >= capturedAt) 0L else capturedAt - endedAt
    }

    /**
     * Media de cum_screen_time_ms/cum_unlocks_count en la MISMA franja de 30
     * min de capturedAt, en cada uno de los 7 días naturales anteriores a la
     * fecha de capturedAt (no a "hoy" global: importa para backfills
     * antiguos). Si un día no tiene lectura en esa franja, no cuenta —
     * cuando aún no hay una semana de histórico esto puede dar medias bajas
     * o en cero (arranque en frío, no resuelto por TIM-23).
     */
    private suspend fun averageForSameSlot(db: AppDatabase, capturedAt: Long): Pair<Double, Double> {
        val targetSlot = DateUtils.halfHourSlot(capturedAt)
        val pastDates = (1..7).map { DateUtils.dateKey(DateUtils.daysAgoFrom(capturedAt, it)) }
        val rows = db.stateSnapshotDao().getForDates(pastDates)

        val latestPerDayInSlot = rows
            .filter { DateUtils.halfHourSlot(it.capturedAt) == targetSlot }
            .groupBy { it.date }
            .mapValues { (_, dayRows) -> dayRows.maxBy { it.capturedAt } }
            .values

        if (latestPerDayInSlot.isEmpty()) return 0.0 to 0.0
        val avgScreen = latestPerDayInSlot.map { it.cumScreenTimeMs.toDouble() }.average()
        val avgUnlocks = latestPerDayInSlot.map { it.cumUnlocksCount.toDouble() }.average()
        return avgScreen to avgUnlocks
    }
}
