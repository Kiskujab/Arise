package com.kiskujab.arise.core

import kotlin.math.max
import kotlin.math.min

data class CriterionResult(
    val key: String,
    val label: String,
    val stat: Stat,
    val weight: Int,
    /** 0..1, 1 = target met. */
    val quality: Double,
    /** Part of the Daily Quest (must hit target to clear it). */
    val mandatory: Boolean,
    val reported: Boolean,
) {
    val met: Boolean get() = quality >= 0.999
}

data class DayScore(
    val results: List<CriterionResult>,
    /** -100..100: weighted quality mapped so 50% quality = 0 XP. */
    val base: Double,
    /** Weighted average quality 0..1. */
    val quality: Double,
    val kcalOver: Int,
    /** Raw calorie penalty before the level factor. */
    val kcalPenalty: Double,
    val productivityBonus: Double,
    val overachieveBonus: Double,
    val questCleared: Boolean,
    val grade: Grade,
) {
    val failedKeys: Set<String> get() = results.filter { it.mandatory && !it.met }.map { it.key }.toSet()
}

/**
 * Turns reported values into criterion qualities.
 *
 * Unreported values count as the worst possible result, which is what a day you skipped gets.
 * [goals] must already include vacation leniency and penalty corrections (see [effectiveGoals]).
 */
object Scoring {

    fun effectiveGoals(base: Goals, vacation: Boolean, penalty: PenaltyQuest?): Goals {
        var g = if (vacation) Rules.vacationGoals(base) else base
        if (penalty != null) g = penalty.applyTo(g)
        return g
    }

    fun effectiveCustomTarget(c: CustomCriterion, vacation: Boolean, penalty: PenaltyQuest?): Double {
        var t = c.target
        if (vacation) t = when (c.type) {
            CustomType.AT_LEAST -> t * 0.5
            CustomType.AT_MOST -> t * 1.5
            CustomType.CHECK -> t
        }
        penalty?.customTargets?.get(c.id)?.let { t = it }
        return t
    }

    fun score(
        values: DayValues,
        goals: Goals,
        customs: List<CustomCriterion> = emptyList(),
        vacation: Boolean = false,
        penalty: PenaltyQuest? = null,
        skipDay: Boolean = false,
    ): DayScore {
        val results = mutableListOf<CriterionResult>()

        val kcalOver = values.kcal?.let { max(0, it - goals.kcal) } ?: Rules.KCAL_OVER_CAP
        val kcalQ = when {
            values.kcal == null -> 0.0
            kcalOver <= Rules.KCAL_TOLERANCE -> 1.0
            else -> max(0.0, 1.0 - kcalOver / Rules.KCAL_ZERO_QUALITY_OVER)
        }
        results += CriterionResult(Keys.KCAL, "Calories", Stat.VIT, Rules.W_KCAL, kcalQ, true, values.kcal != null)

        val proteinQ = ratio(values.protein?.toDouble(), goals.protein.toDouble())
        results += CriterionResult(Keys.PROTEIN, "Protein", Stat.VIT, Rules.W_PROTEIN, proteinQ, true, values.protein != null)

        val stepsR = rawRatio(values.steps?.toDouble(), goals.steps.toDouble())
        val activeR = rawRatio(values.activeKcal?.toDouble(), goals.activeKcal.toDouble())
        val movementQ = (min(1.0, stepsR) + min(1.0, activeR)) / 2.0
        results += CriterionResult(
            Keys.MOVEMENT, "Movement", Stat.STR, Rules.W_MOVEMENT, movementQ, true,
            values.steps != null || values.activeKcal != null,
        )

        val screenQ = atMost(values.screenMinutes?.toDouble(), goals.screenMinutes.toDouble(), goals.screenMinutes.toDouble())
        results += CriterionResult(Keys.SCREEN, "Screen time", Stat.PER, Rules.W_SCREEN, screenQ, true, values.screenMinutes != null)

        val distractionQ = atMost(values.distractionPct?.toDouble(), goals.distractionPct.toDouble(), 30.0)
        results += CriterionResult(
            Keys.DISTRACTION, "Distraction", Stat.PER, Rules.W_DISTRACTION, distractionQ, true, values.distractionPct != null,
        )

        val bandsQ = ratio(values.bandHours, goals.bandHours)
        results += CriterionResult(Keys.BANDS, "Rubber bands", Stat.AGI, Rules.W_BANDS, bandsQ, true, values.bandHours != null)

        val brushQ = ratio(values.brushCount?.toDouble(), goals.brushCount.toDouble())
        results += CriterionResult(Keys.BRUSHING, "Tooth brushing", Stat.AGI, Rules.W_BRUSHING, brushQ, true, values.brushCount != null)

        val skinQ = if (values.skincare == true) 1.0 else 0.0
        // On vacation skincare is optional: it only rewards.
        results += CriterionResult(Keys.SKINCARE, "Evening skincare", Stat.CHA, Rules.W_SKINCARE, skinQ, !vacation, values.skincare != null)

        for (c in customs) {
            val v = values.custom[c.id]
            val target = effectiveCustomTarget(c, vacation, penalty)
            val q = when (c.type) {
                CustomType.CHECK -> if (v != null && v >= 1.0) 1.0 else 0.0
                CustomType.AT_LEAST -> ratio(v, target)
                CustomType.AT_MOST -> atMost(v, target, max(target, 1.0))
            }
            val mandatory = !(vacation && c.type == CustomType.CHECK)
            results += CriterionResult(Keys.custom(c.id), c.name, c.stat, c.weight, q, mandatory, v != null)
        }

        val scored = if (skipDay) results.map { it.copy(quality = 1.0) } else results

        // Non-mandatory criteria only count when they help.
        val counted = scored.filter { it.mandatory || it.met }
        val totalWeight = counted.sumOf { it.weight }.coerceAtLeast(1)
        val quality = counted.sumOf { it.weight * it.quality } / totalWeight
        val base = 100.0 * counted.sumOf { it.weight * (2 * it.quality - 1) } / totalWeight

        val kcalPenalty = if (skipDay) 0.0 else Rules.kcalPenalty(kcalOver)

        val productivityBonus = if (skipDay) 0.0 else min(
            Rules.PRODUCTIVITY_MAX_XP,
            (values.productivityMinutes ?: 0) / Rules.PRODUCTIVITY_MINUTES_PER_XP,
        )
        val bandExtra = ((values.bandHours ?: 0.0).coerceAtMost(Rules.BAND_MAX_HOURS) - goals.bandHours).coerceAtLeast(0.0)
        val movementExtra = (stepsR - 1).coerceAtLeast(0.0) + (activeR - 1).coerceAtLeast(0.0)
        val overachieve = if (skipDay) 0.0 else
            bandExtra * Rules.BAND_BONUS_PER_HOUR + min(Rules.MOVEMENT_BONUS_MAX, movementExtra * 10.0)

        val cleared = scored.filter { it.mandatory }.all { it.met }
        val grade = when {
            quality >= 0.98 && cleared -> Grade.S
            quality >= 0.85 -> Grade.A
            quality >= 0.70 -> Grade.B
            quality >= 0.55 -> Grade.C
            quality >= 0.40 -> Grade.D
            quality >= 0.25 -> Grade.E
            else -> Grade.F
        }

        return DayScore(
            results = scored,
            base = base,
            quality = quality,
            kcalOver = if (skipDay) 0 else kcalOver,
            kcalPenalty = kcalPenalty,
            productivityBonus = productivityBonus,
            overachieveBonus = overachieve,
            questCleared = cleared,
            grade = grade,
        )
    }

    private fun rawRatio(value: Double?, goal: Double): Double = when {
        value == null -> 0.0
        goal <= 0 -> 1.0
        else -> value / goal
    }

    private fun ratio(value: Double?, goal: Double): Double = rawRatio(value, goal).coerceIn(0.0, 1.0)

    /** 1 when at or below [limit], falling linearly to 0 at limit + [zeroSpan]. */
    private fun atMost(value: Double?, limit: Double, zeroSpan: Double): Double = when {
        value == null -> 0.0
        value <= limit -> 1.0
        else -> max(0.0, 1.0 - (value - limit) / zeroSpan.coerceAtLeast(1.0))
    }
}
