package com.minimalist.launcher.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.minimalist.launcher.model.AppInfo

object AppUtils {

    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val prefsManager = PrefsManager(context)
        val ownPackage = context.packageName

        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != ownPackage }
            .map { resolveInfo ->
                val key = "${resolveInfo.activityInfo.packageName}/${resolveInfo.activityInfo.name}"
                AppInfo(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    activityName = resolveInfo.activityInfo.name,
                    customLabel = prefsManager.getCustomLabel(key)
                )
            }
            .sortedBy { it.displayName.lowercase() }
            .distinctBy { it.packageName }
    }

    fun launchApp(context: Context, appInfo: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = appInfo.componentName
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        context.startActivity(intent)
    }
}
