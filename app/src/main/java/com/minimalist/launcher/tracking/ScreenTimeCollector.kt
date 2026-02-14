package com.minimalist.launcher.tracking

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.minimalist.launcher.data.DeviceIdentifier
import com.minimalist.launcher.data.LocalTrackingDb
import com.minimalist.launcher.data.ScreenTimeRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ScreenTimeCollector(private val context: Context) {

    private val db = LocalTrackingDb(context)
    private val deviceId = DeviceIdentifier.getDeviceId(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun hasUsagePermission(): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            cal.timeInMillis,
            System.currentTimeMillis()
        )
        return stats != null && stats.isNotEmpty()
    }

    fun collectToday() {
        if (!hasUsagePermission()) {
            Log.w(TAG, "No usage stats permission")
            return
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()
        val todayStr = dateFormat.format(Date())

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            now
        ) ?: return

        val ownPackage = context.packageName

        for (stat in stats) {
            if (stat.totalTimeInForeground <= 0) continue
            if (stat.packageName == ownPackage) continue

            val appName = try {
                val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                stat.packageName
            }

            val record = ScreenTimeRecord(
                deviceId = deviceId,
                packageName = stat.packageName,
                appName = appName,
                date = todayStr,
                usageDurationMs = stat.totalTimeInForeground,
                openCount = stat.totalTimeInForeground.let {
                    // Estimate open count — UsageStats doesn't give exact count on all API levels
                    if (it > 0) maxOf(1, (it / 60000).toInt()) else 0
                },
                firstUsedAt = if (stat.firstTimeStamp > 0) isoFormat.format(Date(stat.firstTimeStamp)) else null,
                lastUsedAt = if (stat.lastTimeUsed > 0) isoFormat.format(Date(stat.lastTimeUsed)) else null
            )

            db.upsertScreenTime(record)
        }

        Log.d(TAG, "Collected screen time for ${stats.size} apps")
    }

    companion object {
        private const val TAG = "ScreenTimeCollector"
    }
}
