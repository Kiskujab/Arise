package com.kiskujab.arise.data

import com.kiskujab.arise.core.CustomCriterion
import com.kiskujab.arise.core.DayScore
import com.kiskujab.arise.core.Goals
import com.kiskujab.arise.core.PenaltyQuest
import com.kiskujab.arise.core.Scoring

/** A day as the player sees it: its record, the goals in force, and the live score. */
data class DayView(
    val epochDay: Long,
    val record: DayEntity,
    val baseGoals: Goals,
    val goals: Goals,
    val customs: List<CustomCriterion>,
    val vacation: Boolean,
    val penalty: PenaltyQuest?,
    val score: DayScore,
) {
    val remaining get() = score.results.filter { it.mandatory && !it.met }
    val questCount get() = score.results.count { it.mandatory }
    val doneCount get() = score.results.count { it.mandatory && it.met }

    companion object {
        fun build(
            epochDay: Long,
            record: DayEntity?,
            goalsHistory: List<GoalsEntity>,
            customs: List<CustomCriterionEntity>,
            vacations: List<VacationEntity>,
        ): DayView {
            val r = record ?: DayEntity(epochDay)
            val base = goalsHistory.filter { it.effectiveFrom <= epochDay }.maxByOrNull { it.effectiveFrom }
                ?.let { com.kiskujab.arise.core.Codec.decodeWeek(it.goalsJson).forEpochDay(epochDay) } ?: Goals()
            val vacation = vacations.any { it.covers(epochDay) }
            val penalty = r.penaltyQuest
            val active = customs.filter { it.activeOn(epochDay) }.map { it.toCore() }
            val goals = Scoring.effectiveGoals(base, vacation, penalty)
            val score = Scoring.score(r.values, goals, active, vacation, penalty, r.skipDay)
            return DayView(epochDay, r, base, goals, active, vacation, penalty, score)
        }

        suspend fun load(repo: GameRepository, epochDay: Long): DayView {
            val dao = repo.dao
            return build(epochDay, dao.day(epochDay), listOfNotNull(dao.goalsFor(epochDay)), dao.customCriteria(), dao.vacations())
        }
    }
}
