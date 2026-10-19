package com.kiskujab.arise.core

import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Multi-day quests. A 3-Day Trial runs in blocks counted from the awakening day; the Weekly Raid
 * runs Monday to Sunday and has two objectives. They only ever reward: failing costs nothing.
 * Self-development is never a trial objective (it stays bonus-only).
 */
enum class TrialSpan(val days: Int, val label: String, val xp: Int, val gold: Int, val count: Int) {
    TRIAD(3, "3-Day Trial", 40, 150, 1),
    RAID(7, "Weekly Raid", 100, 400, 2),
}

enum class TrialKind(val title: String) {
    UNBROKEN("Unbroken Chain"),
    PROTEIN("Iron Ration"),
    KCAL("Hunger Ward"),
    STEPS("Forced March"),
    BANDS("Iron Jaw Vigil"),
    BRUSH("Pearl Oath"),
    SKIN("Moonlit Ritual"),
    SCREEN("Digital Fast"),
    PERFECT("Flawless Form"),
}

data class Trial(val span: TrialSpan, val kind: TrialKind, val startDay: Long) {
    val endDay: Long get() = startDay + span.days - 1
    val key: String get() = "${span.name}:$startDay:${kind.name}"
    val days: LongRange get() = startDay..endDay

    fun covers(day: Long) = day in days
}

enum class TrialDayStatus { FUTURE, LIVE, FINAL }

/** One day of a trial period as far as it is known. */
data class TrialDay(
    val epochDay: Long,
    /** Goals in force that day (before penalty corrections). */
    val goals: Goals,
    val values: DayValues = DayValues(),
    val results: List<CriterionResult> = emptyList(),
    val status: TrialDayStatus = TrialDayStatus.FUTURE,
    val cleared: Boolean = false,
    val grade: Grade? = null,
    val vacation: Boolean = false,
) {
    fun met(key: String) = results.firstOrNull { it.key == key }?.met == true
}

enum class TrialState { ACTIVE, SECURED, CLEARED, FAILED }

data class TrialProgress(
    val current: Double,
    val target: Double,
    val state: TrialState,
    /** e.g. "2 / 3 days" */
    val text: String,
    /** 0..1 for a progress bar. For "at most" objectives this is how much budget is used. */
    val fraction: Float,
    val lowerIsBetter: Boolean = false,
)

data class TrialReward(val xp: Int, val gold: Int, val items: List<ItemType>)

object Trials {

    fun triadStart(day: Long, awakenedDay: Long): Long = awakenedDay + Math.floorDiv(day - awakenedDay, 3L) * 3L

    fun weekStart(day: Long): Long = day - (LocalDate.ofEpochDay(day).dayOfWeek.value - 1)

    fun periodStart(span: TrialSpan, day: Long, awakenedDay: Long): Long = when (span) {
        TrialSpan.TRIAD -> triadStart(day, awakenedDay)
        TrialSpan.RAID -> weekStart(day)
    }

    /**
     * The trials of the [span] period containing [day]. [firstDayGoals] are the goals of the
     * period's first day; objectives with a zero goal are left out. Empty if the period started
     * before the awakening.
     */
    fun forPeriod(span: TrialSpan, day: Long, awakenedDay: Long, seed: Long, firstDayGoals: Goals): List<Trial> {
        val start = periodStart(span, day, awakenedDay)
        if (start < awakenedDay) return emptyList()
        val pool = TrialKind.entries.filter { k ->
            when (k) {
                TrialKind.STEPS -> firstDayGoals.steps > 0
                TrialKind.BANDS -> firstDayGoals.bandHours > 0
                TrialKind.BRUSH -> firstDayGoals.brushCount > 0
                else -> true
            }
        }.toMutableList()
        val random = Random(seed xor (start * 7919L + span.ordinal * 104_729L))
        val out = mutableListOf<Trial>()
        repeat(span.count) {
            if (pool.isEmpty()) return@repeat
            val k = pool.removeAt(random.nextInt(pool.size))
            out += Trial(span, k, start)
        }
        return out
    }

    fun description(t: Trial): String {
        val d = t.span.days
        return when (t.kind) {
            TrialKind.UNBROKEN -> "Clear the Daily Quest on ${countTarget(t)} of $d days."
            TrialKind.PROTEIN -> "Reach your protein goal on ${countTarget(t)} of $d days."
            TrialKind.KCAL -> "Stay within your calorie limit on ${countTarget(t)} of $d days."
            TrialKind.STEPS -> "Walk 10% more steps in total than your $d-day goals add up to."
            TrialKind.BANDS -> "Wear rubber bands ${bandExtra(t).toInt()} h more in total than your $d-day goals."
            TrialKind.BRUSH -> "Meet your brushing goal on ${countTarget(t)} of $d days."
            TrialKind.SKIN -> "Do evening skincare on ${countTarget(t)} of $d days."
            TrialKind.SCREEN -> "Keep total screen time 10% under your $d-day limits. Report every day."
            TrialKind.PERFECT -> "Earn an S grade on ${countTarget(t)} of $d days."
        }
    }

    private fun countTarget(t: Trial): Int = when (t.span) {
        TrialSpan.TRIAD -> when (t.kind) {
            TrialKind.PERFECT -> 1
            else -> 3
        }
        TrialSpan.RAID -> when (t.kind) {
            TrialKind.UNBROKEN -> 5
            TrialKind.PERFECT -> 3
            TrialKind.BRUSH -> 7
            else -> 6
        }
    }

    private fun bandExtra(t: Trial): Double = if (t.span == TrialSpan.TRIAD) 2.0 else 5.0

    /** [days] must hold one entry per day of the trial, oldest first. */
    fun progress(t: Trial, days: List<TrialDay>): TrialProgress {
        val final = days.count { it.status == TrialDayStatus.FINAL }
        val over = final == t.span.days
        fun countBased(pred: (TrialDay) -> Boolean): TrialProgress {
            val target = countTarget(t)
            val done = days.count { it.status != TrialDayStatus.FUTURE && pred(it) }
            val finalMisses = days.count { it.status == TrialDayStatus.FINAL && !pred(it) }
            val state = when {
                finalMisses > t.span.days - target -> TrialState.FAILED
                over -> TrialState.CLEARED
                days.count { it.status == TrialDayStatus.FINAL && pred(it) } >= target -> TrialState.SECURED
                else -> TrialState.ACTIVE
            }
            return TrialProgress(done.toDouble(), target.toDouble(), state, "$done / $target days", done.toFloat() / target)
        }
        fun sumAtLeast(value: (TrialDay) -> Double, goal: (Goals) -> Double, extra: Double, factor: Double, unit: (Double) -> String): TrialProgress {
            val target = days.sumOf { goal(it.goals) } * factor + extra
            val current = days.filter { it.status != TrialDayStatus.FUTURE }.sumOf(value)
            val finalSum = days.filter { it.status == TrialDayStatus.FINAL }.sumOf(value)
            val state = when {
                over && current >= target -> TrialState.CLEARED
                over -> TrialState.FAILED
                finalSum >= target -> TrialState.SECURED
                else -> TrialState.ACTIVE
            }
            return TrialProgress(current, target, state, "${unit(current)} / ${unit(target)}", if (target <= 0) 1f else (current / target).toFloat().coerceIn(0f, 1f))
        }
        return when (t.kind) {
            TrialKind.UNBROKEN -> countBased { it.cleared }
            TrialKind.PROTEIN -> countBased { it.met(Keys.PROTEIN) }
            TrialKind.KCAL -> countBased { it.met(Keys.KCAL) }
            TrialKind.BRUSH -> countBased { it.met(Keys.BRUSHING) }
            TrialKind.SKIN -> countBased { it.met(Keys.SKINCARE) }
            TrialKind.PERFECT -> countBased { it.grade == Grade.S }
            TrialKind.STEPS -> sumAtLeast({ (it.values.steps ?: 0).toDouble() }, { it.steps.toDouble() }, 0.0, 1.1) { n -> String.format(Locale.US, "%,d", n.roundToInt()) }
            TrialKind.BANDS -> sumAtLeast({ it.values.bandHours ?: 0.0 }, { it.bandHours }, bandExtra(t), 1.0) { h -> String.format(Locale.US, "%.1f h", h) }
            TrialKind.SCREEN -> {
                val target = days.sumOf { it.goals.screenMinutes } * 0.9
                // An unreported finished day counts as a full day on the phone.
                fun minutes(d: TrialDay): Double = when {
                    d.values.screenMinutes != null -> d.values.screenMinutes.toDouble()
                    d.status == TrialDayStatus.FINAL -> 24 * 60.0
                    else -> 0.0
                }
                val current = days.filter { it.status != TrialDayStatus.FUTURE }.sumOf(::minutes)
                val finalSum = days.filter { it.status == TrialDayStatus.FINAL }.sumOf(::minutes)
                val state = when {
                    finalSum > target -> TrialState.FAILED
                    over -> TrialState.CLEARED
                    else -> TrialState.ACTIVE
                }
                TrialProgress(current, target, state, "${hm(current)} / ${hm(target)}", if (target <= 0) 1f else (current / target).toFloat().coerceIn(0f, 1f), lowerIsBetter = true)
            }
        }
    }

    /** Reward for a cleared trial. Halved if any day of it was on vacation. */
    fun reward(t: Trial, days: List<TrialDay>): TrialReward {
        val half = days.any { it.vacation }
        val f = if (half) 0.5 else 1.0
        return TrialReward((t.span.xp * f).roundToInt(), (t.span.gold * f).roundToInt(), emptyList())
    }

    /**
     * Grants the rewards of trials whose last day has been finalized. [finished] pairs each trial
     * with its days. Trials already settled are skipped. Clearing every objective of a Weekly Raid
     * also gives a Random Box.
     */
    fun settle(player: PlayerState, finished: List<Pair<Trial, List<TrialDay>>>): Pair<PlayerState, List<SystemEvent>> {
        val todo = finished.filter { it.first.key !in player.trialResults }
        if (todo.isEmpty()) return player to emptyList()
        val events = mutableListOf<SystemEvent>()
        var xp = 0
        var gold = 0
        val items = mutableListOf<ItemType>()
        val results = player.trialResults.toMutableMap()
        var clears = 0
        for ((t, days) in todo) {
            val ok = progress(t, days).state == TrialState.CLEARED
            results[t.key] = ok
            if (ok) {
                val r = reward(t, days)
                xp += r.xp
                gold += r.gold
                clears++
                events += SystemEvent(EventType.TRIAL_CLEARED, "${t.span.label} Cleared: ${t.kind.title}", "+${r.xp} XP · +${r.gold} gold")
            } else {
                events += SystemEvent(EventType.TRIAL_FAILED, "${t.span.label} Failed: ${t.kind.title}", "The objective was not met. Trials never cost XP — a new one begins.")
            }
        }
        val raids = todo.filter { it.first.span == TrialSpan.RAID }.map { it.first }
        if (raids.size == TrialSpan.RAID.count && raids.all { results[it.key] == true }) {
            items += ItemType.RANDOM_BOX
            events += SystemEvent(EventType.TRIAL_CLEARED, "Raid Boss Defeated", "Every objective of the Weekly Raid was cleared. A Random Box drops.")
        }
        val trimmed = results.entries.toList().takeLast(MAX_RESULTS).associate { it.key to it.value }
        val base = player.copy(
            trialResults = trimmed,
            counters = player.counters.copy(trialsCleared = player.counters.trialsCleared + clears),
        )
        val (p, awardEvents) = Progression.award(base, xp, gold, items)
        return p to events + awardEvents
    }

    private const val MAX_RESULTS = 60

    fun daysLeft(t: Trial, today: Long): Int = (t.endDay - today + 1).coerceIn(0, t.span.days.toLong()).toInt()

    private fun hm(minutes: Double): String {
        val m = minutes.roundToInt()
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }
}
