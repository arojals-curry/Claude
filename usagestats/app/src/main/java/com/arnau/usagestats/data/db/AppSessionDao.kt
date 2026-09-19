package com.arnau.usagestats.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AppSessionDao {

    @Insert
    suspend fun insert(session: AppSessionEntity): Long

    @Update
    suspend fun update(session: AppSessionEntity)

    @Query("SELECT * FROM app_sessions WHERE packageName = :packageName AND endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getOpenSession(packageName: String): AppSessionEntity?

    /** Último instante conocido (cierre o, si sigue abierta, inicio) — de dónde reanudar el escaneo de UsageEvents. */
    @Query("SELECT MAX(COALESCE(endedAt, startedAt)) FROM app_sessions")
    suspend fun getLatestBoundary(): Long?

    /** Tiempo acumulado en foreground ese día hasta [asOf], recortando la sesión abierta o la que termina después de asOf. */
    @Query(
        """
        SELECT COALESCE(SUM(MIN(COALESCE(endedAt, :asOf), :asOf) - startedAt), 0)
        FROM app_sessions
        WHERE date = :date AND startedAt < :asOf
        """
    )
    suspend fun cumulativeScreenTimeMs(date: String, asOf: Long): Long

    /** La sesión más reciente que ya había empezado antes de [asOf] (excluye una que arranque justo en asOf). */
    @Query("SELECT * FROM app_sessions WHERE startedAt < :asOf ORDER BY startedAt DESC LIMIT 1")
    suspend fun mostRecentSessionBefore(asOf: Long): AppSessionEntity?
}
