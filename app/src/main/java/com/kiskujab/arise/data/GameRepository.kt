package com.kiskujab.arise.data

import androidx.room.withTransaction
import com.kiskujab.arise.core.Codec
import com.kiskujab.arise.core.CustomCriterion
import com.kiskujab.arise.core.CustomType
import com.kiskujab.arise.core.DayClock
import com.kiskujab.arise.core.DayResultRecord
import com.kiskujab.arise.core.EventType
import com.kiskujab.arise.core.FinalizeInput
import com.kiskujab.arise.core.FinalizeResult
import com.kiskujab.arise.core.Finalizer
import com.kiskujab.arise.core.Goals
import com.kiskujab.arise.core.ItemType
import com.kiskujab.arise.core.PlayerState
import com.kiskujab.arise.core.Stat
import com.kiskujab.arise.core.Title
import com.kiskujab.arise.core.WeekGoals
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

sealed interface TimeStatus {
    data object Ok : TimeStatus
    /** The device clock is earlier than a time the app has already seen. */
    data class Anomaly(val lastSeen: Long) : TimeStatus
}

/**
 * All writes go through here. Enforces the rules the database alone can't: days lock at midnight,
 * phone usage until noon the next day, and nothing is ever deleted or reset.
 */
class GameRepository(private val db: AriseDatabase) {
    val dao = db.dao()
    private val mutex = Mutex()

    private fun now(): LocalDateTime = LocalDateTime.now()
    private fun today(): Long = LocalDate.now().toEpochDay()

    // ---------------------------------------------------------------- awakening

    suspend fun awaken(name: String, goals: Goals) = mutex.withLock {
        if (dao.player() != null) return@withLock
        val millis = System.currentTimeMillis()
        val today = today()
        db.withTransaction {
            dao.upsertGoals(GoalsEntity(today, Codec.encodeWeek(WeekGoals.uniform(goals))))
            dao.upsertPlayer(
                PlayerEntity(
                    name = name.trim().ifBlank { "Player" },
                    awakenedEpochDay = today,
                    awakenedAt = millis,
                    seed = Random.nextLong(),
                    lastFinalizedDay = today - 1,
                    stateJson = Codec.encodePlayer(PlayerState()),
                    activeTitle = Title.PLAYER.name,
                    maxSeenTime = millis,
                ),
            )
            dao.insertEvent(
                EventEntity(
                    timestamp = millis, epochDay = today, type = EventType.TITLE.name,
                    title = "Welcome, Player",
                    message = "You have been chosen by the System. Your first Daily Quest has arrived. " +
                        "Today is your awakening day: losses are forgiven.",
                    seen = true,
                ),
            )
        }
    }

    // ---------------------------------------------------------------- time & finalization

    /** Checks the clock and finalizes every day whose report window has closed. */
    suspend fun process(): Pair<TimeStatus, List<FinalizeResult>> = mutex.withLock {
        val p = dao.player() ?: return@withLock TimeStatus.Ok to emptyList()
        val millis = System.currentTimeMillis()
        if (millis < p.maxSeenTime - CLOCK_TOLERANCE_MS) {
            return@withLock TimeStatus.Anomaly(p.maxSeenTime) to emptyList()
        }
        if (millis > p.maxSeenTime) dao.upsertPlayer(p.copy(maxSeenTime = millis))
        val results = DayClock.daysToFinalize(LocalDate.ofEpochDay(p.lastFinalizedDay), now())
            .mapNotNull { finalizeLocked(it.toEpochDay()) }
        TimeStatus.Ok to results
    }

    /** Finalize yesterday early (entries are already locked; this closes the phone window too). */
    suspend fun seal(epochDay: Long): FinalizeResult? = mutex.withLock {
        val p = dao.player() ?: return@withLock null
        if (epochDay != p.lastFinalizedDay + 1) return@withLock null
        if (!DayClock.canSeal(LocalDate.ofEpochDay(epochDay), now())) return@withLock null
        finalizeLocked(epochDay)
    }

    private suspend fun finalizeLocked(epochDay: Long): FinalizeResult? = db.withTransaction {
        val p = dao.player() ?: return@withTransaction null
        if (epochDay != p.lastFinalizedDay + 1) return@withTransaction null
        val record = dao.day(epochDay) ?: DayEntity(epochDay)
        val goals = goalsFor(epochDay)
        val customs = customsFor(epochDay)
        val vacations = dao.vacations()
        val vacation = vacations.any { it.covers(epochDay) }
        val input = FinalizeInput(
            epochDay = epochDay,
            values = record.values,
            baseGoals = goals,
            penalty = record.penaltyQuest,
            customs = customs,
            vacation = vacation,
            absent = record.isEmpty && !record.skipDay,
            modifiers = record.modifiers.copy(newcomer = epochDay == p.awakenedEpochDay),
            nextDayBaseGoals = goalsFor(epochDay + 1),
            nextDayVacation = vacations.any { it.covers(epochDay + 1) },
            seed = p.seed,
        )
        val result = Finalizer.finalize(input, p.state)
        val millis = System.currentTimeMillis()

        dao.upsertDay(
            record.copy(
                finalized = true,
                absent = input.absent,
                vacation = vacation,
                goalsJson = Codec.encodeGoals(goals),
                xpDelta = result.appliedDelta,
                grade = result.grade.name,
                questCleared = result.questCleared,
                levelAfter = result.levelAfter,
                resultJson = Codec.encodeResult(
                    DayResultRecord(result.xpLines, result.score.results, result.score.kcalOver, result.score.quality),
                ),
                finalizedAt = millis,
            ),
        )
        val next = dao.day(epochDay + 1) ?: DayEntity(epochDay + 1)
        dao.upsertDay(next.copy(penalty = result.nextPenalty?.encode()))

        val newTitle = result.player.titles - p.state.titles
        val activeTitle = if (p.activeTitle == Title.PLAYER.name && newTitle.isNotEmpty()) newTitle.last().name else p.activeTitle
        dao.upsertPlayer(
            p.copy(lastFinalizedDay = epochDay, stateJson = Codec.encodePlayer(result.player), activeTitle = activeTitle),
        )

        val date = LocalDate.ofEpochDay(epochDay).format(DATE_FORMAT)
        for (e in result.events) {
            val headline = e.type == EventType.DAY_RESULT || e.type == EventType.ABSENT
            dao.insertEvent(
                EventEntity(
                    timestamp = millis, epochDay = epochDay, type = e.type.name,
                    title = if (headline) "$date · ${e.title}" else e.title,
                    message = e.message,
                ),
            )
        }
        result
    }

    // ---------------------------------------------------------------- lookups

    suspend fun goalsFor(epochDay: Long): Goals =
        dao.goalsFor(epochDay)?.let { Codec.decodeWeek(it.goalsJson).forEpochDay(epochDay) } ?: Goals()

    suspend fun customsFor(epochDay: Long): List<CustomCriterion> =
        dao.customCriteria().filter { it.activeOn(epochDay) }.map { it.toCore() }

    // ---------------------------------------------------------------- reporting

    /**
     * Applies [edit] to a day if the time rules allow it: today is fully editable; yesterday only
     * accepts screen time and distraction until noon; anything else is locked.
     */
    suspend fun editDay(epochDay: Long, edit: (DayEntity) -> DayEntity): Boolean = mutex.withLock {
        val p = dao.player() ?: return@withLock false
        if (epochDay <= p.lastFinalizedDay) return@withLock false
        val day = LocalDate.ofEpochDay(epochDay)
        val now = now()
        val current = dao.day(epochDay) ?: DayEntity(epochDay)
        val edited = edit(current)
        val allowed = when {
            DayClock.canEditEntries(day, now) -> edited
            DayClock.canEditPhone(day, now) -> current.copy(screenMinutes = edited.screenMinutes, distractionPct = edited.distractionPct)
            else -> return@withLock false
        }
        dao.upsertDay(allowed.copy(epochDay = epochDay, finalized = false))
        true
    }

    // ---------------------------------------------------------------- items

    /** Uses an item for today. Returns a System message, or an error. */
    suspend fun useItem(item: ItemType): Result<String> = mutex.withLock {
        val p = dao.player() ?: return@withLock Result.failure(IllegalStateException("No player"))
        val state = p.state
        if (state.count(item) <= 0) return@withLock Result.failure(IllegalStateException("You don't have this item."))
        val today = today()
        val day = dao.day(today) ?: DayEntity(today)
        var newState = state.withItem(item, -1)
        val message: String
        when (item) {
            ItemType.XP_BOOSTER -> {
                if (day.booster) return@withLock Result.failure(IllegalStateException("An XP Booster is already active today."))
                if (day.skipDay) return@withLock Result.failure(IllegalStateException("Today is already skipped."))
                dao.upsertDay(day.copy(booster = true))
                message = "XP Booster activated. All XP gained today is doubled."
            }
            ItemType.PENALTY_SHIELD -> {
                if (day.shield) return@withLock Result.failure(IllegalStateException("A shield is already active today."))
                dao.upsertDay(day.copy(shield = true))
                message = "Penalty Shield raised. If today ends in a loss, it will be absorbed."
            }
            ItemType.SKIP_DAY -> {
                if (day.skipDay) return@withLock Result.failure(IllegalStateException("Today is already skipped."))
                dao.upsertDay(day.copy(skipDay = true, booster = false))
                if (day.booster) newState = newState.withItem(ItemType.XP_BOOSTER, 1)
                message = "Skip Day Ticket used. Every quest today is marked as perfectly completed."
            }
            ItemType.RANDOM_BOX -> {
                val got = ItemType.roll(Random.Default, exclude = setOf(ItemType.RANDOM_BOX))
                newState = newState.withItem(got, 1)
                newState = newState.copy(counters = newState.counters.copy(itemsFound = newState.counters.itemsFound + 1))
                message = "The box opens… You obtained: ${got.displayName} [${got.rarity.label}]"
            }
            ItemType.STREAK_FREEZE -> return@withLock Result.failure(IllegalStateException("Streak Freezes are used automatically."))
        }
        val earned = Title.earned(newState) - newState.titles
        newState = newState.copy(titles = newState.titles + earned)
        dao.upsertPlayer(p.copy(stateJson = Codec.encodePlayer(newState)))
        val millis = System.currentTimeMillis()
        dao.insertEvent(EventEntity(timestamp = millis, epochDay = today, type = EventType.ITEM.name, title = item.displayName, message = message))
        for (t in earned) {
            dao.insertEvent(EventEntity(timestamp = millis, epochDay = today, type = EventType.TITLE.name, title = "Title Acquired: ${t.displayName}", message = t.description))
        }
        Result.success(message)
    }

    // ---------------------------------------------------------------- settings that shape the game

    /** New goals apply from tomorrow, so today's quests can't be loosened after the fact. */
    suspend fun setGoals(week: WeekGoals) = mutex.withLock {
        dao.upsertGoals(GoalsEntity(today() + 1, Codec.encodeWeek(week)))
    }

    suspend fun setName(name: String) = mutex.withLock {
        val p = dao.player() ?: return@withLock
        if (name.isNotBlank()) dao.upsertPlayer(p.copy(name = name.trim()))
        Unit
    }

    suspend fun setActiveTitle(title: Title) = mutex.withLock {
        val p = dao.player() ?: return@withLock
        if (title in p.state.titles) dao.upsertPlayer(p.copy(activeTitle = title.name))
        Unit
    }

    /** Vacation starts tomorrow. */
    suspend fun startVacation() = mutex.withLock {
        val tomorrow = today() + 1
        val vs = dao.vacations()
        if (vs.any { it.covers(tomorrow) || it.startDay > tomorrow }) return@withLock
        dao.insertVacation(VacationEntity(startDay = tomorrow))
        Unit
    }

    /** Vacation ends after today (a planned one that hasn't started is cancelled). */
    suspend fun endVacation() = mutex.withLock {
        val today = today()
        for (v in dao.vacations()) {
            when {
                v.startDay > today -> dao.deleteFutureVacation(v.id, today)
                v.covers(today) -> dao.upsertVacation(v.copy(endDay = today))
            }
        }
    }

    /** New custom quests start tomorrow. */
    suspend fun addCustom(name: String, type: CustomType, target: Double, unit: String, stat: Stat) = mutex.withLock {
        dao.insertCustom(
            CustomCriterionEntity(
                name = name.trim(), type = type.name, target = target, unit = unit.trim(), stat = stat.name,
                weight = com.kiskujab.arise.core.Rules.CUSTOM_DEFAULT_WEIGHT, createdDay = today() + 1,
            ),
        )
    }

    /** Retired from tomorrow; history keeps it. A quest that hasn't started yet is simply removed. */
    suspend fun archiveCustom(id: Long) = mutex.withLock {
        val today = today()
        val c = dao.customCriteria().find { it.id == id } ?: return@withLock
        when {
            c.createdDay > today -> dao.deleteFutureCustom(id, today)
            c.archivedDay == null -> dao.upsertCustom(c.copy(archivedDay = today + 1))
        }
        Unit
    }

    suspend fun markSeen(id: Long) = dao.markSeen(id)

    companion object {
        const val CLOCK_TOLERANCE_MS = 10 * 60 * 1000L
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

        fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
