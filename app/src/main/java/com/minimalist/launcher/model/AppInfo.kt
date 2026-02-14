package com.minimalist.launcher.model

import android.content.ComponentName

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val customLabel: String? = null
) {
    val displayName: String
        get() = customLabel ?: label

    val componentName: ComponentName
        get() = ComponentName(packageName, activityName)

    val key: String
        get() = "$packageName/$activityName"
}
