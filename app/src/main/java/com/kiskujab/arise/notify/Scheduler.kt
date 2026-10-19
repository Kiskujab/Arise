package com.kiskujab.arise.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kiskujab.arise.data.NotificationSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

enum class Slot { MORNING, PHONE, PING, EVENING, FINAL, MAINTENANCE }

/**
 * Notifications fire from an exact alarm at the next slot, which then arms the following one.
 * An hourly worker finalizes days, re-arms the alarm and delivers anything that was missed.
 *
 * Versions up to 1.0.4 chained WorkManager jobs instead. Starting the app process replaced the
 * pending job, so when the morning job itself woke the app it cancelled itself and the Daily
 * Quest notification never arrived.
 */
object Scheduler {
    private const val LEGACY_TICK_WORK = "arise_tick"
    private const val MAINTENANCE_WORK = "arise_maintenance"
    private const val ALARM_REQUEST = 4201

    fun slotsFor(date: LocalDate, s: NotificationSettings): List<Pair<LocalDateTime, Slot>> = buildList {
        fun at(minute: Int) = date.atStartOfDay().plusMinutes(minute.toLong())
        if (s.morningEnabled) add(at(s.morningMinute) to Slot.MORNING)
        if (s.phoneReminderEnabled) add(at(s.phoneReminderMinute) to Slot.PHONE)
        if (s.pingsEnabled && s.pingIntervalHours > 0) {
            var m = s.pingStartMinute
            while (m <= s.pingEndMinute) {
                add(at(m) to Slot.PING)
                m += s.pingIntervalHours * 60
            }
        }
        if (s.eveningEnabled) add(at(s.eveningMinute) to Slot.EVENING)
        if (s.finalEnabled) add(at(s.finalMinute) to Slot.FINAL)
        // Day results arrive when the phone window closes at noon.
        add(at(12 * 60 + 1) to Slot.MAINTENANCE)
    }.sortedBy { it.first }

    fun next(now: LocalDateTime, s: NotificationSettings): Pair<LocalDateTime, Slot> {
        val today = now.toLocalDate()
        return (slotsFor(today, s) + slotsFor(today.plusDays(1), s))
            .distinctBy { it.first }
            .first { it.first.isAfter(now) }
    }

    /** The next slot that actually shows a notification, for the settings screen. */
    fun nextVisible(now: LocalDateTime, s: NotificationSettings): Pair<LocalDateTime, Slot>? {
        val today = now.toLocalDate()
        return (slotsFor(today, s) + slotsFor(today.plusDays(1), s))
            .firstOrNull { it.first.isAfter(now) && it.second != Slot.MAINTENANCE }
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(context: Context, s: NotificationSettings) {
        val now = LocalDateTime.now()
        val (time, slot) = next(now, s)
        val millis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_TICK)
            .putExtra(AlarmReceiver.EXTRA_SLOT, slot.name)
            .putExtra(AlarmReceiver.EXTRA_TIME, millis)
        val pi = PendingIntent.getBroadcast(
            context, ALARM_REQUEST, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val am = context.getSystemService(AlarmManager::class.java)
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }

        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(LEGACY_TICK_WORK)
        wm.enqueueUniquePeriodicWork(
            MAINTENANCE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TickWorker>(1, TimeUnit.HOURS)
                .setInputData(Data.Builder().putBoolean(TickWorker.KEY_PERIODIC, true).build())
                .build(),
        )
    }
}
