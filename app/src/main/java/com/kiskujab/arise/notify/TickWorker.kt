package com.kiskujab.arise.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kiskujab.arise.AriseApp

/** Hourly safety net: finalizes days, checks for updates, re-arms the alarm, catches up missed notifications. */
class TickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AriseApp
        try {
            SystemTick.run(app, null, 0L, checkUpdates = true)
        } finally {
            Scheduler.schedule(app, app.settings.notifications.value)
        }
        return Result.success()
    }

    companion object {
        const val KEY_PERIODIC = "periodic"
    }
}
