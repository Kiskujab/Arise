package com.kiskujab.arise.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kiskujab.arise.AriseApp
import com.kiskujab.arise.core.FinalizeResult
import com.kiskujab.arise.data.DayView
import com.kiskujab.arise.data.TimeStatus
import java.time.LocalDate

class TickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AriseApp
        val repo = app.repository
        val settings = app.settings
        val slot = runCatching { Slot.valueOf(inputData.getString(KEY_SLOT) ?: "") }.getOrDefault(Slot.MAINTENANCE)
        val periodic = inputData.getBoolean(KEY_PERIODIC, false)
        val target = inputData.getLong(KEY_TIME, 0L)

        try {
            val player = repo.dao.player() ?: return Result.success()
            val (status, results) = repo.process()
            if (status is TimeStatus.Anomaly) {
                Notifier.post(
                    applicationContext, Notifier.ID_RESULT, Notifier.CHANNEL_QUESTS,
                    "[SYSTEM] Time anomaly detected",
                    "The device clock was moved backwards. The System is locked until the time is correct.",
                )
                return Result.success()
            }
            if (results.isNotEmpty()) postResults(results)

            // Fired early (e.g. rescheduled): don't notify, just re-arm.
            val early = target > 0 && System.currentTimeMillis() < target - 5 * 60_000
            if (periodic) {
                app.updates.checkInBackground()?.let { info ->
                    Notifier.post(
                        applicationContext, Notifier.ID_UPDATE, Notifier.CHANNEL_SYSTEM,
                        "[SYSTEM] Update available: v${info.versionName}",
                        "Open Arise to install it. Your records stay intact.",
                    )
                }
            }
            if (!periodic && !early) notifyFor(slot, repo.let { DayView.load(it, LocalDate.now().toEpochDay()) }, player.lastFinalizedDay)
        } finally {
            if (!periodic) Scheduler.schedule(applicationContext, settings.notifications.value)
        }
        return Result.success()
    }

    private fun postResults(results: List<FinalizeResult>) {
        val last = results.last()
        val text = if (results.size == 1) {
            "Grade ${last.grade.label} · ${signed(last.appliedDelta)} XP · Level ${last.levelAfter}" +
                if (last.nextPenalty != null) "\nPenalty Quest assigned for today." else ""
        } else {
            "${results.size} days were finalized. Total ${signed(results.sumOf { it.appliedDelta })} XP. Level ${last.levelAfter}."
        }
        Notifier.post(applicationContext, Notifier.ID_RESULT, Notifier.CHANNEL_SYSTEM, "[SYSTEM] Day complete", text)
    }

    private suspend fun notifyFor(slot: Slot, today: DayView, lastFinalizedDay: Long) {
        val ctx = applicationContext
        val done = today.score.questCleared
        val remaining = today.remaining.joinToString(", ") { it.label }
        when (slot) {
            Slot.MORNING -> {
                val lines = buildList {
                    if (today.vacation) add("Vacation mode is active.")
                    today.penalty?.let { add("⚠ PENALTY QUEST: " + it.describe(today.customs).joinToString("; ")) }
                    add("Quests: " + today.score.results.filter { it.mandatory }.joinToString(", ") { it.label })
                }
                Notifier.post(ctx, Notifier.ID_MORNING, Notifier.CHANNEL_QUESTS, "[SYSTEM] Daily Quest has arrived", lines.joinToString("\n"))
            }
            Slot.PHONE -> {
                val yesterday = LocalDate.now().minusDays(1).toEpochDay()
                if (yesterday > lastFinalizedDay) {
                    val y = (applicationContext as AriseApp).repository.dao.day(yesterday)
                    if (y?.screenMinutes == null || y.distractionPct == null) {
                        Notifier.post(
                            ctx, Notifier.ID_PHONE, Notifier.CHANNEL_QUESTS, "[SYSTEM] Report yesterday's phone usage",
                            "Enter yesterday's screen time and distraction % from AppBlock before 12:00. Unreported values count as the worst result.",
                        )
                    }
                }
            }
            Slot.PING -> {
                val title = if (done) "[SYSTEM] Daily Quest complete" else "[SYSTEM] ${today.doneCount}/${today.questCount} quests complete"
                Notifier.post(ctx, Notifier.ID_PING, Notifier.CHANNEL_SYSTEM, title, Quotes.random())
            }
            Slot.EVENING -> if (!done) {
                Notifier.post(
                    ctx, Notifier.ID_EVENING, Notifier.CHANNEL_QUESTS, "[SYSTEM] Warning: quests incomplete",
                    "Remaining: $remaining.\nFailing the Daily Quest will result in a penalty.",
                )
            }
            Slot.FINAL -> if (!done) {
                Notifier.post(
                    ctx, Notifier.ID_FINAL, Notifier.CHANNEL_QUESTS, "[SYSTEM] The day locks at midnight",
                    "Remaining: $remaining.\nAfter midnight, entries can no longer be changed.",
                )
            }
            Slot.MAINTENANCE -> Unit
        }
    }

    private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"

    companion object {
        const val KEY_SLOT = "slot"
        const val KEY_TIME = "time"
        const val KEY_PERIODIC = "periodic"
    }
}
