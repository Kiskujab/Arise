package com.kiskujab.arise

import android.app.Application
import com.kiskujab.arise.data.AriseDatabase
import com.kiskujab.arise.data.GameRepository
import com.kiskujab.arise.data.SettingsStore
import com.kiskujab.arise.notify.Notifier
import com.kiskujab.arise.notify.Scheduler

class AriseApp : Application() {
    val database by lazy { AriseDatabase.create(this) }
    val repository by lazy { GameRepository(database) }
    val settings by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Scheduler.schedule(this, settings.notifications.value)
    }
}
