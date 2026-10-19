package com.kiskujab.arise.data

import com.kiskujab.arise.core.Codec
import com.kiskujab.arise.core.Goals
import com.kiskujab.arise.core.Grade
import com.kiskujab.arise.core.Trial
import com.kiskujab.arise.core.TrialDay
import com.kiskujab.arise.core.TrialDayStatus
import com.kiskujab.arise.core.TrialProgress
import com.kiskujab.arise.core.TrialSpan
import com.kiskujab.arise.core.Trials

/** A trial with its live progress, for the screens. */
data class TrialCard(val trial: Trial, val progress: TrialProgress, val daysLeft: Int, val description: String)

/** Turns stored days into [TrialDay]s and builds the current trials. */
object TrialBoard {

    fun goalsAt(day: Long, history: List<GoalsEntity>): Goals =
        history.filter { it.effectiveFrom <= day }.maxByOrNull { it.effectiveFrom }
            ?.let { Codec.decodeWeek(it.goalsJson).forEpochDay(day) } ?: Goals()

    /** A finalized day as stored. */
    fun finalDay(e: DayEntity, fallbackGoals: Goals): TrialDay = TrialDay(
        epochDay = e.epochDay,
        goals = e.goalsJson?.let { runCatching { Codec.decodeGoals(it) }.getOrNull() } ?: fallbackGoals,
        values = e.values,
        results = Codec.decodeResult(e.resultJson)?.results ?: emptyList(),
        status = TrialDayStatus.FINAL,
        cleared = e.questCleared,
        grade = e.grade?.let { runCatching { Grade.valueOf(it) }.getOrNull() },
        vacation = e.vacation,
    )

    fun build(
        today: Long,
        player: PlayerEntity,
        records: Map<Long, DayEntity>,
        goalsHistory: List<GoalsEntity>,
        customs: List<CustomCriterionEntity>,
        vacations: List<VacationEntity>,
    ): List<TrialCard> = TrialSpan.entries.flatMap { span ->
        val start = Trials.periodStart(span, today, player.awakenedEpochDay)
        val trials = Trials.forPeriod(span, today, player.awakenedEpochDay, player.seed, goalsAt(start, goalsHistory))
        if (trials.isEmpty()) return@flatMap emptyList()
        val days = trials.first().days.map { d -> dayFor(d, today, records[d], goalsHistory, customs, vacations) }
        trials.map { t -> TrialCard(t, Trials.progress(t, days), Trials.daysLeft(t, today), Trials.description(t)) }
    }

    private fun dayFor(
        d: Long,
        today: Long,
        record: DayEntity?,
        goalsHistory: List<GoalsEntity>,
        customs: List<CustomCriterionEntity>,
        vacations: List<VacationEntity>,
    ): TrialDay {
        if (record?.finalized == true) return finalDay(record, goalsAt(d, goalsHistory))
        if (d > today) return TrialDay(d, goalsAt(d, goalsHistory), vacation = vacations.any { it.covers(d) })
        val v = DayView.build(d, record, goalsHistory, customs, vacations)
        return TrialDay(
            epochDay = d,
            goals = v.baseGoals,
            values = v.record.values,
            results = v.score.results,
            status = TrialDayStatus.LIVE,
            cleared = v.score.questCleared,
            grade = if (v.record.isEmpty && !v.record.skipDay) Grade.F else v.score.grade,
            vacation = v.vacation,
        )
    }
}
