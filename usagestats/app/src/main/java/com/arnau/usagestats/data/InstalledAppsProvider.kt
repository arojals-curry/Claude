package com.arnau.usagestats.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String
)

/**
 * Enumera las apps con una activity de lanzamiento (MAIN/LAUNCHER). No requiere
 * QUERY_ALL_PACKAGES: basta con declarar en el manifest un <queries> con este
 * mismo intent para que PackageManager las vea en Android 11+.
 */
object InstalledAppsProvider {

    fun getLaunchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                AppEntry(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName,
                    activityName = info.activityInfo.name
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launchIntentFor(app: AppEntry): Intent =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
}
