package com.kiskujab.arise.notify

import com.kiskujab.arise.AriseApp
import com.kiskujab.arise.core.FinalizeResult
import com.kiskujab.arise.data.DayView
import com.kiskujab.arise.data.TimeStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One pass of the System in the background: finalize days, show the notification due for [slot]
 * and catch up on once-a-day notifications that were missed (phone off, alarm delayed, …).
 */
object SystemTick {
    private val lock = Mutex()

    /** Once-a-day slots: delivered at most once per day, possibly late. */
    private val ONCE_A_DAY = setOf(Slot.MORNING, Slot.PHONE, Slot.EVENING, Slot.FINAL)

    suspend fun run(app: AriseApp, slot: Slot?, target: Long, checkUpdates: Boolean = false) = lock.withLock {
        val repo = app.repository
        val player = repo.dao.player() ?: return@withLock
        val (status, results) = repo.process()
        if (status is TimeStatus.Anomaly) {
            Notifier.post(
                app, Notifier.ID_RESULT, Notifier.CHANNEL_QUESTS,
                "[SYSTEM] Time anomaly detected",
                "The device clock was moved backwards. The System is locked until the time is correct.",
            )
            return@withLock
        }
        if (results.isNotEmpty()) postResults(app, results)

        if (checkUpdates) {
            app.updates.checkInBackground()?.let { info ->
                Notifier.post(
                    app, Notifier.ID_UPDATE, Notifier.CHANNEL_SYSTEM,
                    "[SYSTEM] Update available: v${info.versionName}",
                    "Open Arise to install it. Your records stay intact.",
                )
            }
        }

        val today = LocalDate.now().toEpochDay()
        // An alarm that fires early (e.g. after the clock changed) only re-arms.
        val early = target > 0 && System.currentTimeMillis() < target - 5 * 60_000
        if (slot != null && slot != Slot.MAINTENANCE && !early) deliver(app, slot, today, player.lastFinalizedDay)
        catchUp(app, today, player.lastFinalizedDay)
    }

    /** Shows the most recent once-a-day notification whose time has passed today but never came. */
    private suspend fun catchUp(app: AriseApp, today: Long, lastFinalizedDay: Long) {
        val s = app.settings.notifications.value
        val minute = LocalDateTime.now().let { it.hour * 60 + it.minute }
        val evening = if (s.eveningEnabled) s.eveningMinute else Int.MAX_VALUE
        val final = if (s.finalEnabled) s.finalMinute else Int.MAX_VALUE
        val due = buildList {
            if (s.morningEnabled && minute >= s.morningMinute && minute < minOf(14 * 60, evening)) add(Slot.MORNING)
            if (s.phoneReminderEnabled && minute >= s.phoneReminderMinute && minute < 12 * 60) add(Slot.PHONE)
            if (s.eveningEnabled && minute >= s.eveningMinute && minute < final) add(Slot.EVENING)
            if (s.finalEnabled && minute >= s.finalMinute) add(Slot.FINAL)
        }
        for (slot in due) {
            if (app.settings.deliveredDay(slot.name) != today) deliver(app, slot, today, lastFinalizedDay)
        }
    }

    private suspend fun deliver(app: AriseApp, slot: Slot, today: Long, lastFinalizedDay: Long) {
        if (slot in ONCE_A_DAY) {
            if (app.settings.deliveredDay(slot.name) == today) return
            app.settings.markDelivered(slot.name, today)
        }
        notifyFor(app, slot, DayView.load(app.repository, today), lastFinalizedDay)
    }

    private fun postResults(app: AriseApp, results: List<FinalizeResult>) {
        val last = results.last()
        val text = if (results.size == 1) {
            "Grade ${last.grade.label} · ${signed(last.appliedDelta)} XP · +${last.goldGained} gold · Level ${last.levelAfter}" +
                if (last.nextPenalty != null) "\nPenalty Quest assigned for today." else ""
        } else {
            "${results.size} days were finalized. Total ${signed(results.sumOf { it.appliedDelta })} XP. Level ${last.levelAfter}."
        }
        Notifier.post(app, Notifier.ID_RESULT, Notifier.CHANNEL_SYSTEM, "[SYSTEM] Day complete", text)
    }

    private suspend fun notifyFor(app: AriseApp, slot: Slot, today: DayView, lastFinalizedDay: Long) {
        val done = today.score.questCleared
        val remaining = today.remaining.joinToString(", ") { it.label }
        when (slot) {
            Slot.MORNING -> {
                val lines = buildList {
                    if (today.vacation) add("Vacation mode is active.")
                    today.penalty?.let { add("⚠ PENALTY QUEST: " + it.describe(today.customs).joinToString("; ")) }
                    add("Quests: " + today.score.results.filter { it.mandatory }.joinToString(", ") { it.label })
                    add("[WARNING] Failure to complete the Daily Quest will result in an appropriate penalty.")
                }
                Notifier.post(app, Notifier.ID_MORNING, Notifier.CHANNEL_QUESTS, "[SYSTEM] Daily Quest has arrived", lines.joinToString("\n"))
            }
            Slot.PHONE -> {
                val yesterday = LocalDate.now().minusDays(1).toEpochDay()
                if (yesterday > lastFinalizedDay) {
                    val y = app.repository.dao.day(yesterday)
                    if (y?.screenMinutes == null || y.distractionPct == null) {
                        Notifier.post(
                            app, Notifier.ID_PHONE, Notifier.CHANNEL_QUESTS, "[SYSTEM] Report yesterday's phone usage",
                            "Enter yesterday's screen time and distraction % from AppBlock before 12:00. Unreported values count as the worst result.",
                        )
                    }
                }
            }
            Slot.PING -> {
                val title = if (done) "[SYSTEM] Daily Quest complete" else "[SYSTEM] ${today.doneCount}/${today.questCount} quests complete"
                Notifier.post(app, Notifier.ID_PING, Notifier.CHANNEL_SYSTEM, title, Quotes.random())
            }
            Slot.EVENING -> if (!done) {
                Notifier.post(
                    app, Notifier.ID_EVENING, Notifier.CHANNEL_QUESTS, "[SYSTEM] Warning: quests incomplete",
                    "Remaining: $remaining.\nFailing the Daily Quest will result in a penalty.",
                )
            }
            Slot.FINAL -> if (!done) {
                Notifier.post(
                    app, Notifier.ID_FINAL, Notifier.CHANNEL_QUESTS, "[SYSTEM] The day locks at midnight",
                    "Remaining: $remaining.\nAfter midnight, entries can no longer be changed.",
                )
            }
            Slot.MAINTENANCE -> Unit
        }
    }

    private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"
}
