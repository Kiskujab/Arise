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
import com.kiskujab.arise.data.DayEntity
import com.kiskujab.arise.data.DayView
import com.kiskujab.arise.data.EventEntity
import com.kiskujab.arise.data.NotificationSettings
import com.kiskujab.arise.data.PlayerEntity
import com.kiskujab.arise.data.TimeStatus
import com.kiskujab.arise.notify.Scheduler
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

    /** Goals that take effect tomorrow, if they differ from today's. */
    val pendingGoals: StateFlow<Goals?> = combine(goalsHistory, todayEpoch) { list, today ->
        list.firstOrNull { it.effectiveFrom > today }?.let { Codec.decodeGoals(it.goalsJson) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            dao.player()
            _loaded.value = true
        }
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

    fun refresh() {
        viewModelScope.launch {
            val (status, _) = repo.process()
            _timeStatus.value = status
        }
    }

    fun awaken(name: String, goals: Goals) {
        viewModelScope.launch {
            repo.awaken(name, goals)
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

    fun setGoals(goals: Goals) {
        viewModelScope.launch {
            repo.setGoals(goals)
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AriseApp
                MainViewModel(app)
            }
        }
    }
}
