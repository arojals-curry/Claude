package com.arnau.usagestats.data.mood

import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** 5 estados (TIM-23), reemplaza los 4 de TIM-9. rank más alto = mejor humor. */
enum class Mood(val label: String, val rank: Int) {
    AGOTADO("agotado", 0),
    INQUIETO("inquieto", 1),
    NEUTRAL("neutral", 2),
    TRANQUILO("tranquilo", 3),
    CONTENTO("contento", 4);

    companion object {
        fun better(a: Mood, b: Mood): Mood = if (a.rank >= b.rank) a else b
    }
}

enum class TriggeredBy(val label: String) {
    SCREEN("screen"),
    UNLOCKS("unlocks"),
    BOTH("both"),
    IDLE("idle")
}

/**
 * Entrada del motor de mood. avg7dScreenTimeMs/avg7dUnlocks son la media de
 * la MISMA franja horaria en los últimos 7 días (no la media del día
 * completo) — ver TIM-23.
 */
data class UsageSignal(
    val cumScreenTimeMs: Long,
    val cumUnlocksCount: Int,
    val avg7dScreenTimeMs: Double,
    val avg7dUnlocks: Double,
    val idleMs: Long
)

data class MoodResult(
    val mood: Mood,
    val triggeredBy: TriggeredBy,
    val screenRatio: Double,
    val unlockRatio: Double,
    val goalScreenTimeMs: Double,
    val goalUnlocks: Double
)

/**
 * Fórmula de TIM-23: combined_ratio es la MEDIA de screen_ratio y
 * unlock_ratio (no el máximo, como en TIM-9), y un suelo por inactividad se
 * aplica después, que solo puede mejorar el resultado.
 */
object MoodCalculator {

    private const val GOAL_FACTOR = 0.9
    private val IDLE_FLOOR_CONTENTO_MS = TimeUnit.HOURS.toMillis(4)
    private val IDLE_FLOOR_TRANQUILO_MS = TimeUnit.HOURS.toMillis(1)
    private const val BOTH_EPSILON = 0.1

    fun moodFor(signal: UsageSignal): MoodResult {
        val goalScreenTimeMs = signal.avg7dScreenTimeMs * GOAL_FACTOR
        val goalUnlocks = signal.avg7dUnlocks * GOAL_FACTOR

        val screenRatio = signal.cumScreenTimeMs / maxOf(goalScreenTimeMs, 1.0)
        val unlockRatio = signal.cumUnlocksCount / maxOf(goalUnlocks, 0.5)
        val combinedRatio = (screenRatio + unlockRatio) / 2.0

        val ratioMood = when {
            combinedRatio < 0.35 -> Mood.CONTENTO
            combinedRatio < 0.7 -> Mood.TRANQUILO
            combinedRatio < 1.0 -> Mood.NEUTRAL
            combinedRatio < 1.5 -> Mood.INQUIETO
            else -> Mood.AGOTADO
        }

        val floorMood = when {
            signal.idleMs >= IDLE_FLOOR_CONTENTO_MS -> Mood.CONTENTO
            signal.idleMs >= IDLE_FLOOR_TRANQUILO_MS -> Mood.TRANQUILO
            else -> null
        }

        val finalMood = if (floorMood != null) Mood.better(ratioMood, floorMood) else ratioMood
        val floorWon = floorMood != null && finalMood == floorMood && finalMood != ratioMood

        val triggeredBy = when {
            floorWon -> TriggeredBy.IDLE
            abs(screenRatio - unlockRatio) < BOTH_EPSILON -> TriggeredBy.BOTH
            screenRatio > unlockRatio -> TriggeredBy.SCREEN
            else -> TriggeredBy.UNLOCKS
        }

        return MoodResult(
            mood = finalMood,
            triggeredBy = triggeredBy,
            screenRatio = screenRatio,
            unlockRatio = unlockRatio,
            goalScreenTimeMs = goalScreenTimeMs,
            goalUnlocks = goalUnlocks
        )
    }
}
