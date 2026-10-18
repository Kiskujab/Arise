package com.kiskujab.arise.core

import kotlinx.serialization.Serializable

import kotlin.math.roundToInt
import kotlin.random.Random

/** Items activated for a specific day. */
data class DayModifiers(
    val booster: Boolean = false,
    val shield: Boolean = false,
    val skipDay: Boolean = false,
    /** The awakening day: losses are forgiven and no penalty quest follows. */
    val newcomer: Boolean = false,
)

data class FinalizeInput(
    val epochDay: Long,
    val values: DayValues,
    /** Goals in force that day, before vacation leniency and penalty corrections. */
    val baseGoals: Goals,
    val penalty: PenaltyQuest?,
    val customs: List<CustomCriterion>,
    val vacation: Boolean,
    /** The player never reported anything that day. */
    val absent: Boolean,
    val modifiers: DayModifiers = DayModifiers(),
    val nextDayBaseGoals: Goals,
    val nextDayVacation: Boolean,
    /** Seed for item drops; derived from the install so results are reproducible. */
    val seed: Long,
)

@Serializable
data class XpLine(val label: String, val amount: Int)

enum class EventType {
    DAY_RESULT, ABSENT, LEVEL_UP, LEVEL_DOWN, RANK_UP, RANK_DOWN, ITEM, TITLE,
    FREEZE_USED, STREAK_BROKEN, PENALTY_ASSIGNED, PENALTY_CLEARED, PENALTY_FAILED, SHIELD_USED,
}

data class SystemEvent(val type: EventType, val title: String, val message: String)

data class FinalizeResult(
    val player: PlayerState,
    val score: DayScore,
    val xpLines: List<XpLine>,
    /** XP change requested by the day's result. */
    val rawDelta: Int,
    /** XP change actually applied (the safe zone may absorb part of a loss). */
    val appliedDelta: Int,
    val levelBefore: Int,
    val levelAfter: Int,
    val questCleared: Boolean,
    val grade: Grade,
    val nextPenalty: PenaltyQuest?,
    val itemsGained: List<ItemType>,
    val events: List<SystemEvent>,
)

object Finalizer {

    fun finalize(input: FinalizeInput, player: PlayerState): FinalizeResult {
        val skip = input.modifiers.skipDay
        val goals = Scoring.effectiveGoals(input.baseGoals, input.vacation, input.penalty)
        val score = Scoring.score(input.values, goals, input.customs, input.vacation, input.penalty, skip)
        val grade = if (input.absent && !skip) Grade.F else score.grade
        val events = mutableListOf<SystemEvent>()

        val levelBefore = player.level
        val rankBefore = player.rank
        val lf = Rules.levelFactor(levelBefore)
        val vac = if (input.vacation) Rules.VACATION_MULTIPLIER else 1.0
        val boost = if (input.modifiers.booster) 2.0 else 1.0

        // --- XP ---
        val lines = mutableListOf<XpLine>()
        fun gain(label: String, v: Double) {
            val n = (v * vac * boost).roundToInt()
            if (n != 0) lines += XpLine(label, n)
        }
        fun loss(label: String, v: Double) {
            val n = (v * vac).roundToInt()
            if (n != 0) lines += XpLine(label, -n)
        }

        if (score.base >= 0) {
            gain("Quest performance", score.base)
            gain("Streak bonus (${player.streak} days)", score.base * Rules.streakBonus(player.streak))
        } else {
            loss(if (input.absent) "Absent: worst result recorded" else "Quest failure", -score.base * lf)
        }
        if (score.kcalPenalty > 0) {
            loss("Calorie overflow (+${score.kcalOver} kcal)", score.kcalPenalty * lf)
        }
        gain("Self-development", score.productivityBonus)
        gain("Overachievement", score.overachieveBonus)

        val penalty = input.penalty
        var penaltyCleared: Boolean? = null
        if (penalty != null && !skip) {
            val metKeys = score.results.filter { it.met }.map { it.key }.toSet()
            penaltyCleared = metKeys.containsAll(penalty.keys)
            if (penaltyCleared) {
                gain("Penalty Quest cleared", Rules.PENALTY_CLEARED_XP.toDouble())
                events += SystemEvent(EventType.PENALTY_CLEARED, "Penalty Quest Cleared", "You corrected your mistakes. The System acknowledges it.")
            } else {
                loss("Penalty Quest failed", Rules.PENALTY_FAILED_XP * lf)
                events += SystemEvent(EventType.PENALTY_FAILED, "Penalty Quest Failed", "You failed to correct your mistakes. Additional XP has been taken.")
            }
        }

        var rawDelta = lines.sumOf { it.amount }
        if (rawDelta < 0 && input.modifiers.newcomer) {
            lines += XpLine("Newcomer's Blessing", -rawDelta)
            rawDelta = 0
        } else if (rawDelta < 0 && input.modifiers.shield) {
            lines += XpLine("Penalty Shield", -rawDelta)
            events += SystemEvent(EventType.SHIELD_USED, "Penalty Shield", "The shield absorbed ${-rawDelta} XP of damage.")
            rawDelta = 0
        }

        val floorXp = Rules.xpForLevel(Rules.safeFloor(levelBefore))
        val newXp = maxOf(player.xp + rawDelta, floorXp)
        val appliedDelta = (newXp - player.xp).toInt()
        var p = player.copy(xp = newXp)
        val levelAfter = p.level

        // --- Streak ---
        val cleared = score.questCleared
        if (cleared) {
            val s = p.streak + 1
            p = p.copy(streak = s, bestStreak = maxOf(p.bestStreak, s))
            if (s % Rules.FREEZE_EVERY_STREAK_DAYS == 0 && p.count(ItemType.STREAK_FREEZE) < Rules.MAX_FREEZES) {
                p = p.withItem(ItemType.STREAK_FREEZE, 1)
                events += SystemEvent(EventType.ITEM, "Reward: Streak Freeze", "$s-day streak reached. A Streak Freeze has been added to your inventory.")
            }
        } else if (p.streak > 0) {
            if (p.count(ItemType.STREAK_FREEZE) > 0) {
                p = p.withItem(ItemType.STREAK_FREEZE, -1)
                events += SystemEvent(EventType.FREEZE_USED, "Streak Freeze Used", "Your ${p.streak}-day streak was preserved.")
            } else {
                events += SystemEvent(EventType.STREAK_BROKEN, "Streak Broken", "Your ${p.streak}-day streak has ended.")
                p = p.copy(streak = 0)
            }
        }

        // --- Items ---
        val gained = mutableListOf<ItemType>()
        val random = Random(input.seed * 31 + input.epochDay)
        if (cleared && random.nextDouble() < ItemType.dropChance(p.streak)) {
            gained += ItemType.roll(random)
        }
        if (levelAfter > p.maxLevel) {
            for (lvl in (p.maxLevel + 1)..levelAfter) {
                if (lvl % Rules.SAFE_ZONE_STEP == 0) gained += ItemType.RANDOM_BOX
            }
        }
        for (item in gained) {
            p = p.withItem(item, 1)
            events += SystemEvent(EventType.ITEM, "Item Acquired: ${item.displayName}", "[${item.rarity.label}] ${item.description}")
        }

        // --- Levels and rank ---
        if (levelAfter > levelBefore) {
            events += SystemEvent(EventType.LEVEL_UP, "Level Up!", "You have reached level $levelAfter.")
        } else if (levelAfter < levelBefore) {
            events += SystemEvent(
                EventType.LEVEL_DOWN, "Level Down",
                "You fell to level $levelAfter. Safe zone: level ${Rules.safeFloor(levelAfter)}.",
            )
        }
        val rankAfter = Rank.forLevel(levelAfter)
        if (rankAfter > rankBefore) {
            events += SystemEvent(EventType.RANK_UP, "Rank Up!", "You are now ${rankAfter.title}.")
        } else if (rankAfter < rankBefore) {
            events += SystemEvent(EventType.RANK_DOWN, "Rank Lost", "You have fallen to ${rankAfter.title}.")
        }

        var recoveryTitle = false
        p = when {
            levelAfter > p.maxLevel -> p.copy(maxLevel = levelAfter, recoveryTarget = null).also { recoveryTitle = p.recoveryTarget != null }
            levelAfter < p.maxLevel && p.recoveryTarget == null -> p.copy(recoveryTarget = p.maxLevel)
            p.recoveryTarget != null && levelAfter >= p.recoveryTarget!! -> p.copy(recoveryTarget = null).also { recoveryTitle = true }
            else -> p
        }

        // --- Stats and counters ---
        p = p.copy(statPoints = updatedStats(p.statPoints, score, input))
        p = p.copy(counters = updatedCounters(p.counters, score, input, cleared, grade, penaltyCleared, gained.size))

        // --- Titles ---
        val earned = Title.earned(p) + (if (recoveryTitle) setOf(Title.RISEN) else emptySet())
        val newTitles = earned - p.titles
        p = p.copy(titles = p.titles + newTitles)
        for (t in newTitles) events += SystemEvent(EventType.TITLE, "Title Acquired: ${t.displayName}", t.description)

        // --- Tomorrow's penalty ---
        val nextPenalty = if (!cleared && !skip && !input.modifiers.newcomer && !input.nextDayVacation) {
            PenaltyQuest.fromFailures(score, input.customs, input.nextDayBaseGoals)
        } else {
            null
        }
        if (nextPenalty != null) {
            events += SystemEvent(
                EventType.PENALTY_ASSIGNED, "Penalty Quest Assigned",
                "You failed the Daily Quest. Tomorrow's quests are adjusted:\n• " + nextPenalty.describe(input.customs).joinToString("\n• "),
            )
        }

        val headline = if (input.absent && !skip) {
            SystemEvent(EventType.ABSENT, "Absence Detected", "You did not report. The worst possible result has been recorded. ${signed(appliedDelta)} XP.")
        } else {
            SystemEvent(EventType.DAY_RESULT, "Day Complete — Grade ${grade.label}", (if (cleared) "Daily Quest cleared. " else "Daily Quest failed. ") + "${signed(appliedDelta)} XP.")
        }

        return FinalizeResult(
            player = p,
            score = score,
            xpLines = lines,
            rawDelta = rawDelta,
            appliedDelta = appliedDelta,
            levelBefore = levelBefore,
            levelAfter = levelAfter,
            questCleared = cleared,
            grade = grade,
            nextPenalty = nextPenalty,
            itemsGained = gained,
            events = listOf(headline) + events,
        )
    }

    private fun updatedStats(current: Map<Stat, Double>, score: DayScore, input: FinalizeInput): Map<Stat, Double> {
        val m = current.toMutableMap()
        val vac = if (input.vacation) Rules.VACATION_MULTIPLIER else 1.0
        for (r in score.results) {
            val d = when {
                r.met -> 1.0 * vac
                r.quality >= 0.6 -> 0.4 * vac
                !r.mandatory -> 0.0
                else -> -0.3
            }
            // Spread across criteria sharing a stat so each stat moves by at most ~1/day.
            val share = score.results.count { it.stat == r.stat }.toDouble()
            m[r.stat] = ((m[r.stat] ?: 0.0) + d / share).coerceAtLeast(1.0 - PlayerState.BASE_STAT)
        }
        val minutes = input.values.productivityMinutes ?: 0
        m[Stat.INT] = (m[Stat.INT] ?: 0.0) + (minutes / 30.0).coerceAtMost(3.0) * vac
        return m
    }

    private fun updatedCounters(
        c: Counters,
        score: DayScore,
        input: FinalizeInput,
        cleared: Boolean,
        grade: Grade,
        penaltyCleared: Boolean?,
        itemsFound: Int,
    ): Counters {
        fun met(key: String) = score.results.firstOrNull { it.key == key }?.met == true
        val v = input.values
        return c.copy(
            daysTracked = c.daysTracked + 1,
            daysAbsent = c.daysAbsent + if (input.absent) 1 else 0,
            vacationDays = c.vacationDays + if (input.vacation) 1 else 0,
            questClears = c.questClears + if (cleared) 1 else 0,
            perfectDays = c.perfectDays + if (grade == Grade.S) 1 else 0,
            penaltiesCleared = c.penaltiesCleared + if (penaltyCleared == true) 1 else 0,
            penaltiesFailed = c.penaltiesFailed + if (penaltyCleared == false) 1 else 0,
            kcalDays = c.kcalDays + if (met(Keys.KCAL)) 1 else 0,
            movementDays = c.movementDays + if (met(Keys.MOVEMENT)) 1 else 0,
            bandDays = c.bandDays + if (met(Keys.BANDS)) 1 else 0,
            brushDays = c.brushDays + if (met(Keys.BRUSHING)) 1 else 0,
            skinDays = c.skinDays + if (met(Keys.SKINCARE)) 1 else 0,
            focusDays = c.focusDays + if (met(Keys.SCREEN) && met(Keys.DISTRACTION)) 1 else 0,
            totalSteps = c.totalSteps + (v.steps ?: 0),
            productivityMinutes = c.productivityMinutes + (v.productivityMinutes ?: 0),
            itemsFound = c.itemsFound + itemsFound,
        )
    }

    private fun signed(n: Int) = if (n >= 0) "+$n" else "$n"
}
