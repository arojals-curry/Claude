package com.minimalist.launcher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceRecord(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_model") val deviceModel: String,
    @SerialName("android_version") val androidVersion: String,
    @SerialName("app_version") val appVersion: String
)

@Serializable
data class AppLaunchRecord(
    @SerialName("device_id") val deviceId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String,
    @SerialName("launched_at") val launchedAt: String,
    @SerialName("launch_source") val launchSource: String
)

@Serializable
data class ScreenTimeRecord(
    @SerialName("device_id") val deviceId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String,
    @SerialName("date") val date: String,
    @SerialName("usage_duration_ms") val usageDurationMs: Long,
    @SerialName("open_count") val openCount: Int,
    @SerialName("first_used_at") val firstUsedAt: String? = null,
    @SerialName("last_used_at") val lastUsedAt: String? = null
)
