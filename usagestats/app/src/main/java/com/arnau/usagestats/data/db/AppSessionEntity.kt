package com.arnau.usagestats.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Sesión de una app en foreground, emparejando MOVE_TO_FOREGROUND/BACKGROUND.
 * Alimenta el cálculo de tiempo acumulado e inactividad del motor de mood
 * (TIM-23) — es independiente de usage_daily, que sigue viniendo de
 * queryUsageStats.
 */
@Entity(
    tableName = "app_sessions",
    indices = [Index("date"), Index(value = ["packageName", "date"])]
)
data class AppSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val date: String,
    val startedAt: Long,
    /** NULL si la sesión sigue abierta (no ha llegado su MOVE_TO_BACKGROUND todavía). */
    val endedAt: Long? = null,
    val durationMs: Long? = null
)
