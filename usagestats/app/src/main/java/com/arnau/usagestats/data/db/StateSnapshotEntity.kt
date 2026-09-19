package com.arnau.usagestats.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Snapshot del estado de uso, cada ~30 min ('tick_30min') o en cada
 * desbloqueo ('unlock'). Solo las filas 'unlock' llevan mood/triggeredBy
 * calculado — las de 'tick_30min' son puro histórico para las medias de
 * franja horaria (TIM-23 §8.3), nadie las ve.
 */
@Entity(
    tableName = "state_snapshots",
    indices = [Index("date")]
)
data class StateSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Instante lógico que describe la fila (puede ser muy anterior a writtenAt en un backfill). */
    val capturedAt: Long,
    /** Instante real en que se escribió la fila. */
    val writtenAt: Long,
    val date: String,
    /** "tick_30min" | "unlock" */
    val trigger: String,
    val cumScreenTimeMs: Long,
    val cumUnlocksCount: Int,
    val dailyGoalMs: Long,
    val avg7dScreenTimeMs: Double,
    val goalScreenTimeMs: Double,
    val avg7dUnlocks: Double,
    val goalUnlocks: Double,
    val screenRatio: Double,
    val unlockRatio: Double,
    val idleMs: Long,
    /** NULL en tick_30min; calculado en unlock. */
    val mood: String? = null,
    /** NULL en tick_30min. "screen" | "unlocks" | "both" | "idle" en unlock. */
    val triggeredBy: String? = null
)
