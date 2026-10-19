package com.kiskujab.arise.notify

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Everything that can stop notifications from arriving, and where to fix it. */
data class NotificationHealth(
    val notificationsAllowed: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val allGood: Boolean get() = notificationsAllowed && exactAlarms && batteryUnrestricted

    companion object {
        fun check(context: Context): NotificationHealth = NotificationHealth(
            notificationsAllowed = Notifier.canPost(context),
            exactAlarms = Scheduler.canScheduleExact(context),
            batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
        )

        fun notificationSettings(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                appDetails(context)
            }

        fun exactAlarmSettings(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            } else {
                appDetails(context)
            }

        @SuppressLint("BatteryLife")
        fun batterySettings(context: Context): Intent =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

        private fun appDetails(context: Context): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
}
