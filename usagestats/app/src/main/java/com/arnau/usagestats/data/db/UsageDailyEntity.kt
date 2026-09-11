package com.arnau.usagestats.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_daily",
    indices = [Index(value = ["packageName", "date"], unique = true)]
)
data class UsageDailyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    /** Fecha en formato YYYY-MM-DD, zona horaria del dispositivo. */
    val date: String,
    val durationMs: Long,
    val opensCount: Int
)
