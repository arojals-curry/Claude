package com.minimalist.launcher.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocalTrackingDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_LAUNCHES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                package_name TEXT NOT NULL,
                app_name TEXT NOT NULL,
                launched_at TEXT NOT NULL,
                launch_source TEXT NOT NULL,
                synced INTEGER DEFAULT 0
            )
        """)

        db.execSQL("""
            CREATE TABLE $TABLE_SCREEN_TIME (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL,
                package_name TEXT NOT NULL,
                app_name TEXT NOT NULL,
                date TEXT NOT NULL,
                usage_duration_ms INTEGER NOT NULL,
                open_count INTEGER NOT NULL,
                first_used_at TEXT,
                last_used_at TEXT,
                synced INTEGER DEFAULT 0,
                UNIQUE(device_id, package_name, date) ON CONFLICT REPLACE
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LAUNCHES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SCREEN_TIME")
        onCreate(db)
    }

    // ---- App Launches ----

    fun insertLaunch(record: AppLaunchRecord) {
        writableDatabase.insert(TABLE_LAUNCHES, null, ContentValues().apply {
            put("device_id", record.deviceId)
            put("package_name", record.packageName)
            put("app_name", record.appName)
            put("launched_at", record.launchedAt)
            put("launch_source", record.launchSource)
            put("synced", 0)
        })
    }

    fun getUnsyncedLaunches(limit: Int = 100): List<Pair<Long, AppLaunchRecord>> {
        val results = mutableListOf<Pair<Long, AppLaunchRecord>>()
        val cursor = readableDatabase.query(
            TABLE_LAUNCHES, null,
            "synced = 0", null, null, null,
            "id ASC", "$limit"
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow("id"))
                val record = AppLaunchRecord(
                    deviceId = it.getString(it.getColumnIndexOrThrow("device_id")),
                    packageName = it.getString(it.getColumnIndexOrThrow("package_name")),
                    appName = it.getString(it.getColumnIndexOrThrow("app_name")),
                    launchedAt = it.getString(it.getColumnIndexOrThrow("launched_at")),
                    launchSource = it.getString(it.getColumnIndexOrThrow("launch_source"))
                )
                results.add(id to record)
            }
        }
        return results
    }

    fun markLaunchesSynced(ids: List<Long>) {
        if (ids.isEmpty()) return
        val placeholders = ids.joinToString(",") { "?" }
        writableDatabase.execSQL(
            "UPDATE $TABLE_LAUNCHES SET synced = 1 WHERE id IN ($placeholders)",
            ids.map { it.toString() }.toTypedArray()
        )
    }

    // ---- Screen Time ----

    fun upsertScreenTime(record: ScreenTimeRecord) {
        writableDatabase.insertWithOnConflict(
            TABLE_SCREEN_TIME, null,
            ContentValues().apply {
                put("device_id", record.deviceId)
                put("package_name", record.packageName)
                put("app_name", record.appName)
                put("date", record.date)
                put("usage_duration_ms", record.usageDurationMs)
                put("open_count", record.openCount)
                put("first_used_at", record.firstUsedAt)
                put("last_used_at", record.lastUsedAt)
                put("synced", 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getUnsyncedScreenTime(limit: Int = 200): List<Pair<Long, ScreenTimeRecord>> {
        val results = mutableListOf<Pair<Long, ScreenTimeRecord>>()
        val cursor = readableDatabase.query(
            TABLE_SCREEN_TIME, null,
            "synced = 0", null, null, null,
            "id ASC", "$limit"
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow("id"))
                val record = ScreenTimeRecord(
                    deviceId = it.getString(it.getColumnIndexOrThrow("device_id")),
                    packageName = it.getString(it.getColumnIndexOrThrow("package_name")),
                    appName = it.getString(it.getColumnIndexOrThrow("app_name")),
                    date = it.getString(it.getColumnIndexOrThrow("date")),
                    usageDurationMs = it.getLong(it.getColumnIndexOrThrow("usage_duration_ms")),
                    openCount = it.getInt(it.getColumnIndexOrThrow("open_count")),
                    firstUsedAt = it.getString(it.getColumnIndexOrThrow("first_used_at")),
                    lastUsedAt = it.getString(it.getColumnIndexOrThrow("last_used_at"))
                )
                results.add(id to record)
            }
        }
        return results
    }

    fun markScreenTimeSynced(ids: List<Long>) {
        if (ids.isEmpty()) return
        val placeholders = ids.joinToString(",") { "?" }
        writableDatabase.execSQL(
            "UPDATE $TABLE_SCREEN_TIME SET synced = 1 WHERE id IN ($placeholders)",
            ids.map { it.toString() }.toTypedArray()
        )
    }

    fun cleanOldSyncedRecords(daysToKeep: Int = 30) {
        val cutoff = System.currentTimeMillis() - daysToKeep * 86_400_000L
        val cutoffDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(cutoff))
        writableDatabase.delete(TABLE_LAUNCHES, "synced = 1 AND launched_at < ?", arrayOf(cutoffDate))
        writableDatabase.delete(TABLE_SCREEN_TIME, "synced = 1 AND date < ?", arrayOf(cutoffDate))
    }

    companion object {
        private const val DB_NAME = "tracking.db"
        private const val DB_VERSION = 1
        private const val TABLE_LAUNCHES = "app_launches"
        private const val TABLE_SCREEN_TIME = "screen_time"
    }
}
