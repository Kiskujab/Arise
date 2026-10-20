package com.kiskujab.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LossCapTest {

    private val goals = Goals()

    private fun input(values: DayValues, absent: Boolean) = FinalizeInput(
        epochDay = 20000L, values = values, baseGoals = goals, penalty = null, customs = emptyList(),
        vacation = false, absent = absent, nextDayBaseGoals = goals, nextDayVacation = false, seed = 42,
    )

    private fun player(level: Int) = PlayerState(xp = Rules.xpForLevel(level) + 500, maxLevel = level)

    @Test fun absentDayAtHighLevelLosesAtMostTheCap() {
        val r = Finalizer.finalize(input(DayValues(), absent = true), player(48))
        assertEquals(-Rules.MAX_DAILY_LOSS, r.appliedDelta)
        assertTrue(r.xpLines.any { it.label == "Daily loss cap" })
    }

    @Test fun smallLossesAreUntouched() {
        val slightlyOver = DayValues(
            kcal = goals.kcal + 200, protein = goals.protein, steps = goals.steps, activeKcal = 300,
            screenMinutes = 60, distractionPct = 5, bandHours = goals.bandHours, brushCount = goals.brushCount, skincare = true,
        )
        val r = Finalizer.finalize(input(slightlyOver, absent = false), player(8))
        assertTrue(r.xpLines.none { it.label == "Daily loss cap" })
    }
}
