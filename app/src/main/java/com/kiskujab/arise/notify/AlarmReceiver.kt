package com.kiskujab.arise.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kiskujab.arise.AriseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Fired by the exact alarm at a notification slot. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AriseApp
        val slot = runCatching { Slot.valueOf(intent.getStringExtra(EXTRA_SLOT) ?: "") }.getOrNull()
        val target = intent.getLongExtra(EXTRA_TIME, 0L)
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(9_000) { SystemTick.run(app, slot, target) }
            } catch (_: Exception) {
                // Never let a failed tick stop the next alarm from being armed.
            } finally {
                Scheduler.schedule(app, app.settings.notifications.value)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.kiskujab.arise.TICK"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_TIME = "time"
        internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

/**
 * Re-arms the alarm after a reboot, an app update, or a change of the phone's clock or time zone,
 * and delivers anything that was missed meanwhile.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AriseApp
        val pending = goAsync()
        AlarmReceiver.scope.launch {
            try {
                withTimeoutOrNull(9_000) { SystemTick.run(app, null, 0L) }
            } catch (_: Exception) {
            } finally {
                Scheduler.schedule(app, app.settings.notifications.value)
                pending.finish()
            }
        }
    }
}
