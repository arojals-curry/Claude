package com.minimalist.launcher.tracking

import android.content.Context
import android.util.Log
import com.minimalist.launcher.data.AppLaunchRecord
import com.minimalist.launcher.data.DeviceIdentifier
import com.minimalist.launcher.data.LocalTrackingDb
import com.minimalist.launcher.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ActivityTracker private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = LocalTrackingDb(appContext)
    private val deviceId = DeviceIdentifier.getDeviceId(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun logAppLaunch(appInfo: AppInfo, source: LaunchSource) {
        scope.launch {
            try {
                val record = AppLaunchRecord(
                    deviceId = deviceId,
                    packageName = appInfo.packageName,
                    appName = appInfo.displayName,
                    launchedAt = isoFormat.format(Date()),
                    launchSource = source.value
                )
                db.insertLaunch(record)
                Log.d(TAG, "Logged launch: ${appInfo.displayName} from ${source.value}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log launch", e)
            }
        }
    }

    companion object {
        private const val TAG = "ActivityTracker"

        @Volatile
        private var instance: ActivityTracker? = null

        fun getInstance(context: Context): ActivityTracker {
            return instance ?: synchronized(this) {
                instance ?: ActivityTracker(context).also { instance = it }
            }
        }
    }

    enum class LaunchSource(val value: String) {
        FAVORITES("favorites"),
        DRAWER("drawer"),
        SEARCH("search"),
        CONTEXT_MENU("context_menu")
    }
}
