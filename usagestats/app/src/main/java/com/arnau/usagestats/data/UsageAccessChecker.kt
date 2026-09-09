package com.arnau.usagestats.data

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * PACKAGE_USAGE_STATS es un permiso especial: no aparece en el diálogo runtime
 * estándar, así que hay que comprobarlo vía AppOpsManager.
 */
object UsageAccessChecker {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
