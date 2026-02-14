package com.minimalist.launcher.tracking

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.minimalist.launcher.BuildConfig
import com.minimalist.launcher.data.DeviceIdentifier
import com.minimalist.launcher.data.DeviceRecord
import com.minimalist.launcher.data.LocalTrackingDb
import com.minimalist.launcher.data.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = LocalTrackingDb(applicationContext)
            val collector = ScreenTimeCollector(applicationContext)

            // 1. Collect latest screen time data
            collector.collectToday()

            // 2. Register/update device
            syncDevice()

            // 3. Sync app launches
            syncLaunches(db)

            // 4. Sync screen time
            syncScreenTime(db)

            // 5. Clean old synced records
            db.cleanOldSyncedRecords()

            Log.d(TAG, "Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }

    private suspend fun syncDevice() {
        val deviceId = DeviceIdentifier.getDeviceId(applicationContext)
        val record = DeviceRecord(
            deviceId = deviceId,
            deviceModel = DeviceIdentifier.getDeviceModel(),
            androidVersion = DeviceIdentifier.getAndroidVersion(),
            appVersion = BuildConfig.VERSION_NAME
        )
        SupabaseProvider.client.postgrest["devices"].upsert(record) {
            onConflict = "device_id"
        }
    }

    private suspend fun syncLaunches(db: LocalTrackingDb) {
        val unsyncedLaunches = db.getUnsyncedLaunches()
        if (unsyncedLaunches.isEmpty()) return

        val ids = unsyncedLaunches.map { it.first }
        val records = unsyncedLaunches.map { it.second }

        // Batch insert in chunks
        records.chunked(50).forEach { chunk ->
            SupabaseProvider.client.postgrest["app_launches"].insert(chunk)
        }

        db.markLaunchesSynced(ids)
        Log.d(TAG, "Synced ${records.size} launches")
    }

    private suspend fun syncScreenTime(db: LocalTrackingDb) {
        val unsyncedTime = db.getUnsyncedScreenTime()
        if (unsyncedTime.isEmpty()) return

        val ids = unsyncedTime.map { it.first }
        val records = unsyncedTime.map { it.second }

        records.chunked(50).forEach { chunk ->
            SupabaseProvider.client.postgrest["screen_time"].upsert(chunk) {
                onConflict = "device_id,package_name,date"
            }
        }

        db.markScreenTimeSynced(ids)
        Log.d(TAG, "Synced ${records.size} screen time records")
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "tracking_sync"
    }
}
