package com.ttv20.rsyncbackup.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

data class AppPermissionState(
    val allFilesAccess: Boolean,
    val batteryOptimizationExempt: Boolean,
    val exactAlarmAccess: Boolean,
    val wifiStateAccess: Boolean,
    val notifications: Boolean,
) {
    val allRequiredGranted: Boolean
        get() = allFilesAccess &&
            batteryOptimizationExempt &&
            exactAlarmAccess &&
            notifications
}

class PermissionStateReader(private val context: Context) {
    fun read(): AppPermissionState {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return AppPermissionState(
            allFilesAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager(),
            batteryOptimizationExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName),
            exactAlarmAccess = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms(),
            wifiStateAccess = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
            notifications = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
}

object PermissionIntents {
    fun allFilesAccess(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData(Uri.parse("package:${context.packageName}"))

    fun batteryOptimization(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))

    fun exactAlarm(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        }

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
}
