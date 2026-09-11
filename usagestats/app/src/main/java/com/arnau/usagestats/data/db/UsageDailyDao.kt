package com.arnau.usagestats.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

data class PackageDurationSum(
    val packageName: String,
    val totalDuration: Long
)

@Dao
interface UsageDailyDao {

    @Query(
        """
        UPDATE usage_daily SET durationMs = :durationMs, opensCount = :opensCount
        WHERE packageName = :packageName AND date = :date
        """
    )
    suspend fun update(packageName: String, date: String, durationMs: Long, opensCount: Int): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: UsageDailyEntity): Long

    /** Upsert por (packageName, date): intenta actualizar y, si no había fila, inserta una nueva. */
    @Transaction
    suspend fun upsert(packageName: String, date: String, durationMs: Long, opensCount: Int) {
        val rowsUpdated = update(packageName, date, durationMs, opensCount)
        if (rowsUpdated == 0) {
            insert(
                UsageDailyEntity(
                    packageName = packageName,
                    date = date,
                    durationMs = durationMs,
                    opensCount = opensCount
                )
            )
        }
    }

    @Query("SELECT * FROM usage_daily WHERE date = :date ORDER BY durationMs DESC")
    suspend fun getForDate(date: String): List<UsageDailyEntity>

    @Query(
        """
        SELECT packageName, SUM(durationMs) AS totalDuration
        FROM usage_daily
        WHERE date IN (:dates)
        GROUP BY packageName
        """
    )
    suspend fun sumDurationByPackage(dates: List<String>): List<PackageDurationSum>
}
