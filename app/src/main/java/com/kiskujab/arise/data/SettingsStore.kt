package com.kiskujab.arise.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Notification preferences. Times are minutes after midnight. */
data class NotificationSettings(
    val morningEnabled: Boolean = true,
    val morningMinute: Int = 8 * 60,
    val phoneReminderEnabled: Boolean = true,
    val phoneReminderMinute: Int = 10 * 60,
    val pingsEnabled: Boolean = true,
    val pingIntervalHours: Int = 2,
    val pingStartMinute: Int = 11 * 60,
    val pingEndMinute: Int = 19 * 60,
    val eveningEnabled: Boolean = true,
    val eveningMinute: Int = 21 * 60,
    val finalEnabled: Boolean = true,
    val finalMinute: Int = 23 * 60,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("arise_settings", Context.MODE_PRIVATE)
    private val _notifications = MutableStateFlow(load())
    val notifications: StateFlow<NotificationSettings> = _notifications.asStateFlow()

    private fun load(): NotificationSettings {
        val d = NotificationSettings()
        return NotificationSettings(
            morningEnabled = prefs.getBoolean("morningEnabled", d.morningEnabled),
            morningMinute = prefs.getInt("morningMinute", d.morningMinute),
            phoneReminderEnabled = prefs.getBoolean("phoneReminderEnabled", d.phoneReminderEnabled),
            phoneReminderMinute = prefs.getInt("phoneReminderMinute", d.phoneReminderMinute),
            pingsEnabled = prefs.getBoolean("pingsEnabled", d.pingsEnabled),
            pingIntervalHours = prefs.getInt("pingIntervalHours", d.pingIntervalHours),
            pingStartMinute = prefs.getInt("pingStartMinute", d.pingStartMinute),
            pingEndMinute = prefs.getInt("pingEndMinute", d.pingEndMinute),
            eveningEnabled = prefs.getBoolean("eveningEnabled", d.eveningEnabled),
            eveningMinute = prefs.getInt("eveningMinute", d.eveningMinute),
            finalEnabled = prefs.getBoolean("finalEnabled", d.finalEnabled),
            finalMinute = prefs.getInt("finalMinute", d.finalMinute),
        )
    }

    private val state = context.getSharedPreferences("arise_state", Context.MODE_PRIVATE)

    /** Epoch day a once-a-day notification was last delivered. */
    fun deliveredDay(slot: String): Long = state.getLong("delivered_$slot", Long.MIN_VALUE)

    fun markDelivered(slot: String, day: Long) {
        state.edit().putLong("delivered_$slot", day).commit()
    }

    /** Epoch day the daily arrival animation was last shown. */
    var lastIntroDay: Long
        get() = state.getLong("lastIntroDay", Long.MIN_VALUE)
        set(value) {
            state.edit().putLong("lastIntroDay", value).apply()
        }

    fun update(transform: (NotificationSettings) -> NotificationSettings) {
        val n = transform(_notifications.value)
        prefs.edit()
            .putBoolean("morningEnabled", n.morningEnabled)
            .putInt("morningMinute", n.morningMinute)
            .putBoolean("phoneReminderEnabled", n.phoneReminderEnabled)
            .putInt("phoneReminderMinute", n.phoneReminderMinute)
            .putBoolean("pingsEnabled", n.pingsEnabled)
            .putInt("pingIntervalHours", n.pingIntervalHours)
            .putInt("pingStartMinute", n.pingStartMinute)
            .putInt("pingEndMinute", n.pingEndMinute)
            .putBoolean("eveningEnabled", n.eveningEnabled)
            .putInt("eveningMinute", n.eveningMinute)
            .putBoolean("finalEnabled", n.finalEnabled)
            .putInt("finalMinute", n.finalMinute)
            .apply()
        _notifications.value = n
    }
}
