package com.kiskujab.arise.notify

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kiskujab.arise.data.NotificationSettings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

enum class Slot { MORNING, PHONE, PING, EVENING, FINAL, MAINTENANCE }

/**
 * One chained worker fires at the next notification time, then schedules the following one.
 * A periodic worker finalizes days even when no notification is due.
 */
object Scheduler {
    private const val TICK_WORK = "arise_tick"
    private const val MAINTENANCE_WORK = "arise_maintenance"

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

    fun schedule(context: Context, s: NotificationSettings) {
        val now = LocalDateTime.now()
        val (time, slot) = next(now, s)
        val delay = Duration.between(now, time).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<TickWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(TickWorker.KEY_SLOT, slot.name)
                    .putLong(TickWorker.KEY_TIME, time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                    .build(),
            )
            .build()
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniqueWork(TICK_WORK, ExistingWorkPolicy.REPLACE, request)
        wm.enqueueUniquePeriodicWork(
            MAINTENANCE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TickWorker>(1, TimeUnit.HOURS)
                .setInputData(Data.Builder().putString(TickWorker.KEY_SLOT, Slot.MAINTENANCE.name).putBoolean(TickWorker.KEY_PERIODIC, true).build())
                .build(),
        )
    }
}
