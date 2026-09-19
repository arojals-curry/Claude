package com.arnau.usagestats.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface StateSnapshotDao {

    @Insert
    suspend fun insertAll(snapshots: List<StateSnapshotEntity>)

    @Query("SELECT * FROM state_snapshots ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatest(): StateSnapshotEntity?

    @Query("SELECT * FROM state_snapshots WHERE trigger = 'unlock' ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatestUnlockSnapshot(): StateSnapshotEntity?

    @Query("SELECT * FROM state_snapshots WHERE date = :date ORDER BY capturedAt ASC")
    suspend fun getForDate(date: String): List<StateSnapshotEntity>

    /** Para calcular la media de 7 días por franja horaria: se agrupa/filtra en Kotlin, no aquí. */
    @Query("SELECT * FROM state_snapshots WHERE date IN (:dates) ORDER BY capturedAt ASC")
    suspend fun getForDates(dates: List<String>): List<StateSnapshotEntity>
}
