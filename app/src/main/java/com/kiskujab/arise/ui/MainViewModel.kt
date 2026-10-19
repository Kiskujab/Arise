package com.kiskujab.arise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kiskujab.arise.AriseApp
import com.kiskujab.arise.core.Codec
import com.kiskujab.arise.core.CustomType
import com.kiskujab.arise.core.Goals
import com.kiskujab.arise.core.ItemType
import com.kiskujab.arise.core.PlayerState
import com.kiskujab.arise.core.Stat
import com.kiskujab.arise.core.Title
import com.kiskujab.arise.core.WeekGoals
import com.kiskujab.arise.data.DayEntity
import com.kiskujab.arise.data.DayView
import com.kiskujab.arise.data.EventEntity
import com.kiskujab.arise.data.NotificationSettings
import com.kiskujab.arise.data.PlayerEntity
import com.kiskujab.arise.data.TimeStatus
import com.kiskujab.arise.core.Equipment
import com.kiskujab.arise.core.Loadout
import com.kiskujab.arise.data.TrialBoard
import com.kiskujab.arise.data.TrialCard
import com.kiskujab.arise.notify.NotificationHealth
import com.kiskujab.arise.notify.Notifier
import com.kiskujab.arise.notify.Scheduler
import com.kiskujab.arise.notify.Slot
import com.kiskujab.arise.update.UpdateState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

enum class Intro { BOOT, DAILY }

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val app: AriseApp) : ViewModel() {
    private val repo = app.repository
    private val dao = repo.dao

    private val _now = MutableStateFlow(LocalDateTime.now())
    val now: StateFlow<LocalDateTime> = _now.asStateFlow()

    private val _timeStatus = MutableStateFlow<TimeStatus>(TimeStatus.Ok)
    val timeStatus: StateFlow<TimeStatus> = _timeStatus.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val player: StateFlow<PlayerEntity?> = dao.playerFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val playerState: StateFlow<PlayerState> = player
        .map { it?.state ?: PlayerState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerState())

    private val todayEpoch = _now.map { it.toLocalDate().toEpochDay() }.distinctUntilChanged()

    val goalsHistory = dao.goalsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val customs = dao.customCriteriaFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val vacations = dao.vacationsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun dayViewFlow(offset: Long) = todayEpoch.flatMapLatest { today ->
        val day = today + offset
        combine(dao.dayFlow(day), goalsHistory, customs, vacations) { record, goals, cs, vs ->
            DayView.build(day, record, goals, cs, vs)
        }
    }

    val today: StateFlow<DayView?> = dayViewFlow(0).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val yesterday: StateFlow<DayView?> = dayViewFlow(-1).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val history: StateFlow<List<DayEntity>> = dao.finalizedDaysFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val unseenEvents: StateFlow<List<EventEntity>> = dao.unseenEventsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val eventLog: StateFlow<List<EventEntity>> = dao.eventLogFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notificationSettings: StateFlow<NotificationSettings> = app.settings.notifications

    val updateState: StateFlow<UpdateState> = app.updates.state

    /** Week goals that take effect tomorrow, if any were saved. */
    val pendingGoals: StateFlow<WeekGoals?> = combine(goalsHistory, todayEpoch) { list, today ->
        list.firstOrNull { it.effectiveFrom > today }?.let { Codec.decodeWeek(it.goalsJson) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Week goals in force today. */
    val currentWeekGoals: StateFlow<WeekGoals> = combine(goalsHistory, todayEpoch) { list, today ->
        list.filter { it.effectiveFrom <= today }.maxByOrNull { it.effectiveFrom }?.let { Codec.decodeWeek(it.goalsJson) }
            ?: WeekGoals.uniform(Goals())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeekGoals.uniform(Goals()))

    /** Days of the current trial periods, for live trial progress. */
    val trials: StateFlow<List<TrialCard>> = todayEpoch.flatMapLatest { today ->
        combine(dao.daysFromFlow(today - 7), player, goalsHistory, customs, vacations) { days, p, g, cs, vs ->
            if (p == null) emptyList() else TrialBoard.build(today, p, days.associateBy { it.epochDay }, g, cs, vs)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _intro = MutableStateFlow<Intro?>(null)
    /** The entry animation to show: every launch, and a longer one on the first open of the day. */
    val intro: StateFlow<Intro?> = _intro.asStateFlow()

    init {
        viewModelScope.launch {
            val p = dao.player()
            val (status, _) = repo.process()
            _timeStatus.value = status
            if (p != null && status is TimeStatus.Ok) {
                _intro.value = if (app.settings.lastIntroDay != todayEpochNow()) Intro.DAILY else Intro.BOOT
            }
            _loaded.value = true
        }
        viewModelScope.launch { app.updates.check() }
        viewModelScope.launch {
            var ticks = 0
            while (true) {
                val previous = _now.value
                _now.value = LocalDateTime.now()
                // Re-check rules every 30 s and at every day / noon boundary.
                if (ticks % 30 == 0 || previous.toLocalDate() != _now.value.toLocalDate() || previous.hour != _now.value.hour) refresh()
                ticks++
                delay(1000)
            }
        }
    }

    private fun todayEpochNow() = java.time.LocalDate.now().toEpochDay()

    /** Back in the foreground: re-check the rules, re-arm notifications, greet a new day. */
    fun onResume() {
        refresh()
        Scheduler.schedule(app, app.settings.notifications.value)
        if (_loaded.value && player.value != null && _intro.value == null && app.settings.lastIntroDay != todayEpochNow()) {
            _intro.value = Intro.DAILY
        }
    }

    fun dismissIntro() {
        if (_intro.value == Intro.DAILY) {
            val today = todayEpochNow()
            app.settings.lastIntroDay = today
            // Seen in the app: no need for the morning notification to catch up later.
            val n = LocalDateTime.now()
            if (n.hour * 60 + n.minute >= app.settings.notifications.value.morningMinute) {
                app.settings.markDelivered(Slot.MORNING.name, today)
            }
        }
        _intro.value = null
    }

    fun setLoadout(l: Loadout) = viewModelScope.launch {
        repo.setLoadout(l).onSuccess { _messages.tryEmit("[SYSTEM] $it") }.onFailure { _messages.tryEmit(it.message ?: "Failed") }
    }

    fun buyEquipment(e: Equipment) = viewModelScope.launch {
        repo.buyEquipment(e).onSuccess { _messages.tryEmit("[SYSTEM] $it") }.onFailure { _messages.tryEmit(it.message ?: "Failed") }
    }

    fun buyItem(item: ItemType) = viewModelScope.launch {
        repo.buyItem(item).onSuccess { _messages.tryEmit("[SYSTEM] $it") }.onFailure { _messages.tryEmit(it.message ?: "Failed") }
    }

    fun notificationHealth(): NotificationHealth = NotificationHealth.check(app)

    fun sendTestNotification() {
        if (!Notifier.canPost(app)) {
            _messages.tryEmit("[SYSTEM] Notifications are blocked. Allow them first.")
            return
        }
        Notifier.post(app, Notifier.ID_TEST, Notifier.CHANNEL_QUESTS, "[SYSTEM] Test notification", "The System can reach you. Quests will arrive on time.")
    }

    fun refresh() {
        viewModelScope.launch {
            val (status, _) = repo.process()
            _timeStatus.value = status
        }
    }

    fun awaken(name: String, goals: Goals) {
        viewModelScope.launch {
            repo.awaken(name, goals)
            app.settings.lastIntroDay = todayEpochNow()
            Scheduler.schedule(app, app.settings.notifications.value)
        }
    }

    fun editToday(edit: (DayEntity) -> DayEntity) = editDay(_now.value.toLocalDate().toEpochDay(), edit)

    fun editDay(epochDay: Long, edit: (DayEntity) -> DayEntity) {
        viewModelScope.launch {
            if (!repo.editDay(epochDay, edit)) _messages.tryEmit("[SYSTEM] This record is locked.")
        }
    }

    fun seal(epochDay: Long) {
        viewModelScope.launch {
            if (repo.seal(epochDay) == null) _messages.tryEmit("[SYSTEM] This day can't be sealed yet.")
        }
    }

    fun useItem(item: ItemType) {
        viewModelScope.launch {
            repo.useItem(item).onFailure { _messages.tryEmit(it.message ?: "Failed") }
        }
    }

    fun setGoals(week: WeekGoals) {
        viewModelScope.launch {
            repo.setGoals(week)
            _messages.tryEmit("[SYSTEM] New goals take effect tomorrow.")
        }
    }

    fun setName(name: String) = viewModelScope.launch { repo.setName(name) }
    fun setTitle(title: Title) = viewModelScope.launch { repo.setActiveTitle(title) }

    fun startVacation() = viewModelScope.launch {
        repo.startVacation()
        _messages.tryEmit("[SYSTEM] Vacation begins tomorrow.")
    }

    fun endVacation() = viewModelScope.launch {
        repo.endVacation()
        _messages.tryEmit("[SYSTEM] Normal quests resume tomorrow.")
    }

    fun addCustom(name: String, type: CustomType, target: Double, unit: String, stat: Stat) = viewModelScope.launch {
        repo.addCustom(name, type, target, unit, stat)
        _messages.tryEmit("[SYSTEM] New quest registered. It starts tomorrow.")
    }

    fun archiveCustom(id: Long) = viewModelScope.launch { repo.archiveCustom(id) }

    fun markSeen(id: Long) = viewModelScope.launch { repo.markSeen(id) }

    fun updateNotifications(transform: (NotificationSettings) -> NotificationSettings) {
        app.settings.update(transform)
        Scheduler.schedule(app, app.settings.notifications.value)
    }

    fun checkForUpdates() = viewModelScope.launch { app.updates.check(showProgress = true) }

    /** Downloads the update if needed, then opens the system installer. */
    fun installUpdate() = viewModelScope.launch {
        val file = when (val s = app.updates.state.value) {
            is UpdateState.Ready -> s.file
            is UpdateState.Available -> app.updates.download(s.info)
            is UpdateState.Failed -> s.info?.let { app.updates.download(it) }
            else -> null
        } ?: return@launch
        if (!app.updates.install(file)) {
            _messages.tryEmit("[SYSTEM] Allow Arise to install apps, then come back and tap INSTALL again.")
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AriseApp
                MainViewModel(app)
            }
        }
    }
}
