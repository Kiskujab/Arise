package com.kiskujab.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RulesTest {

    private val goals = Goals()

    private val perfect = DayValues(
        kcal = 2100, protein = 160, steps = 8000, activeKcal = 300,
        screenMinutes = 120, distractionPct = 10, bandHours = 20.0, brushCount = 2, skincare = true,
    )

    private fun input(values: DayValues, absent: Boolean = false, vacation: Boolean = false, penalty: PenaltyQuest? = null, mods: DayModifiers = DayModifiers()) =
        FinalizeInput(
            epochDay = 20000, values = values, baseGoals = goals, penalty = penalty, customs = emptyList(),
            vacation = vacation, absent = absent, modifiers = mods, nextDayBaseGoals = goals, nextDayVacation = false, seed = 42,
        )

    private fun playerAt(level: Int, streak: Int = 0) = PlayerState(xp = Rules.xpForLevel(level), maxLevel = level, streak = streak, bestStreak = streak)

    @Test fun levelCurveIsConsistent() {
        assertEquals(0, Rules.xpForLevel(1))
        for (l in 1..Rules.MAX_LEVEL) {
            assertEquals(l, Rules.levelForXp(Rules.xpForLevel(l)))
            if (l > 1) assertTrue(Rules.xpForLevel(l) > Rules.xpForLevel(l - 1))
        }
        assertEquals(Rules.MAX_LEVEL, Rules.levelForXp(Long.MAX_VALUE / 2))
    }

    @Test fun sRankTakesAboutAYearOfGoodDays() {
        val daysToS = Rules.xpForLevel(Rank.S.minLevel) / 110.0
        assertTrue("days to S = $daysToS", daysToS in 300.0..420.0)
    }

    @Test fun safeZones() {
        assertEquals(1, Rules.safeFloor(1))
        assertEquals(1, Rules.safeFloor(4))
        assertEquals(5, Rules.safeFloor(5))
        assertEquals(15, Rules.safeFloor(18))
        assertEquals(60, Rules.safeFloor(64))
    }

    @Test fun perfectDayGivesAboutHundred() {
        val r = Finalizer.finalize(input(perfect), playerAt(5))
        assertTrue(r.questCleared)
        assertEquals(Grade.S, r.grade)
        assertEquals(100, r.appliedDelta)
        assertEquals(1, r.player.streak)
    }

    @Test fun thousandKcalOverAtLevel18DropsToSafeZone15() {
        val r = Finalizer.finalize(input(perfect.copy(kcal = goals.kcal + 1000)), playerAt(18))
        assertEquals(15, r.levelAfter)
        assertFalse(r.questCleared)
        assertNotNull(r.nextPenalty)
        assertEquals(PenaltyQuest.MAX_KCAL_CUT, r.nextPenalty!!.kcalCut)
    }

    @Test fun kcalPenaltyIsExponential() {
        val p200 = Rules.kcalPenalty(200)
        val p600 = Rules.kcalPenalty(600)
        val p1000 = Rules.kcalPenalty(1000)
        assertTrue(p200 < 20)
        assertTrue(p600 > 5 * p200)
        assertTrue(p1000 > 4 * p600)
        // A small overshoot is a small loss, still a net positive day.
        val small = Finalizer.finalize(input(perfect.copy(kcal = goals.kcal + 200)), playerAt(18))
        assertTrue(small.appliedDelta > 0)
        val big = Finalizer.finalize(input(perfect.copy(kcal = goals.kcal + 600)), playerAt(18))
        assertTrue(big.appliedDelta < -100)
    }

    @Test fun mistakesCostMoreAtHigherLevels() {
        val bad = perfect.copy(kcal = goals.kcal + 600, steps = 1000, activeKcal = 0)
        val low = Finalizer.finalize(input(bad), playerAt(16))
        val high = Finalizer.finalize(input(bad), playerAt(62))
        assertTrue(high.rawDelta < low.rawDelta * 2)
    }

    @Test fun absentDayRecordsWorstAndFallsToFloor() {
        val r = Finalizer.finalize(input(DayValues(), absent = true), playerAt(18, streak = 4))
        assertEquals(Grade.F, r.grade)
        assertEquals(15, r.levelAfter)
        assertEquals(0, r.player.streak)
        assertTrue(r.events.any { it.type == EventType.ABSENT })
        assertTrue(r.events.any { it.type == EventType.STREAK_BROKEN })
        assertEquals(1, r.player.counters.daysAbsent)
    }

    @Test fun cannotFallBelowLevelOne() {
        val r = Finalizer.finalize(input(DayValues(), absent = true), PlayerState(xp = 30))
        assertEquals(0L, r.player.xp)
        assertEquals(1, r.levelAfter)
    }

    @Test fun streakFreezeSavesStreak() {
        val p = playerAt(10, streak = 12).withItem(ItemType.STREAK_FREEZE, 1)
        val r = Finalizer.finalize(input(perfect.copy(skincare = false)), p)
        assertEquals(12, r.player.streak)
        assertEquals(0, r.player.count(ItemType.STREAK_FREEZE))
        assertTrue(r.events.any { it.type == EventType.FREEZE_USED })
    }

    @Test fun sevenDayStreakGrantsFreeze() {
        val r = Finalizer.finalize(input(perfect), playerAt(10, streak = 6))
        assertEquals(7, r.player.streak)
        assertTrue(r.player.count(ItemType.STREAK_FREEZE) >= 1)
        assertTrue(Title.WOLF_SLAYER in r.player.titles)
    }

    @Test fun vacationIsLenientAndHalved() {
        val lazy = perfect.copy(steps = 4000, activeKcal = 150, screenMinutes = 250, skincare = false)
        val normal = Finalizer.finalize(input(lazy), playerAt(10))
        val vac = Finalizer.finalize(input(lazy, vacation = true), playerAt(10))
        assertFalse(normal.questCleared)
        assertTrue(vac.questCleared)
        // 100 × 0.5, plus the 4 h band overachievement (vacation goal is 16 h) × 3 × 0.5.
        assertEquals(56, vac.appliedDelta)
        assertEquals(1, vac.player.counters.vacationDays)
    }

    @Test fun newcomerDayForgivesLosses() {
        val r = Finalizer.finalize(input(DayValues(), absent = true, mods = DayModifiers(newcomer = true)), playerAt(1))
        assertEquals(0, r.appliedDelta)
        assertNull(r.nextPenalty)
    }

    @Test fun skipDayIsPerfect() {
        val r = Finalizer.finalize(input(DayValues(), mods = DayModifiers(skipDay = true)), playerAt(10))
        assertTrue(r.questCleared)
        assertEquals(Grade.S, r.grade)
        assertEquals(100, r.appliedDelta)
    }

    @Test fun boosterDoublesGainsAndShieldBlocksLoss() {
        val boosted = Finalizer.finalize(input(perfect, mods = DayModifiers(booster = true)), playerAt(10))
        assertEquals(200, boosted.appliedDelta)
        val shielded = Finalizer.finalize(input(DayValues(), absent = true, mods = DayModifiers(shield = true)), playerAt(18))
        assertEquals(0, shielded.appliedDelta)
    }

    @Test fun penaltyQuestCorrectsOnlyFailures() {
        val r = Finalizer.finalize(input(perfect.copy(screenMinutes = 300, brushCount = 1)), playerAt(10))
        val pq = r.nextPenalty!!
        assertEquals(20, pq.screenCutPct)
        assertEquals(1, pq.brushExtra)
        assertEquals(0, pq.kcalCut)
        assertEquals(0, pq.movementBoostPct)
        assertEquals(setOf(Keys.SCREEN, Keys.BRUSHING), pq.keys)
    }

    @Test fun penaltyQuestStaysAchievable() {
        val maxed = goals.copy(bandHours = 22.0, brushCount = 3, screenMinutes = 30, distractionPct = 5)
        val score = Scoring.score(DayValues(), maxed)
        val pq = PenaltyQuest.fromFailures(score, emptyList(), maxed)!!
        val g = pq.applyTo(maxed)
        assertTrue(g.bandHours <= 22.0)
        assertTrue(g.brushCount <= 3)
        assertTrue(g.screenMinutes >= 30)
        assertTrue(g.distractionPct >= 5)
        assertTrue(Keys.BANDS in pq.required)
        assertTrue(Keys.BRUSHING in pq.required)
        assertTrue(g.kcal >= goals.kcal - PenaltyQuest.MAX_KCAL_CUT)
    }

    @Test fun penaltyQuestRoundTrips() {
        val pq = PenaltyQuest(kcalCut = 250, movementBoostPct = 15, bandExtraHours = 1.0, customTargets = mapOf(3L to 11.0), required = setOf(Keys.PROTEIN, "custom:4"))
        assertEquals(pq, PenaltyQuest.decode(pq.encode()))
        assertNull(PenaltyQuest.decode(""))
    }

    @Test fun clearingPenaltyRewardsAndFailingCosts() {
        val pq = PenaltyQuest(screenCutPct = 20)
        val cleared = Finalizer.finalize(input(perfect, penalty = pq), playerAt(10))
        assertTrue(cleared.events.any { it.type == EventType.PENALTY_CLEARED })
        assertTrue(Title.OVERCOMER in cleared.player.titles)
        val failed = Finalizer.finalize(input(perfect.copy(screenMinutes = 160), penalty = pq), playerAt(10))
        assertTrue(failed.events.any { it.type == EventType.PENALTY_FAILED })
    }

    @Test fun recoveringLostLevelGrantsTitle() {
        var p = playerAt(13)
        p = Finalizer.finalize(input(DayValues(), absent = true), p).player
        assertEquals(10, p.level)
        assertEquals(13, p.recoveryTarget)
        repeat(40) { p = Finalizer.finalize(input(perfect), p).player }
        assertTrue(Title.RISEN in p.titles)
        assertNull(p.recoveryTarget)
    }

    @Test fun reachingSafeZoneGivesRandomBox() {
        val p = PlayerState(xp = Rules.xpForLevel(5) - 10, maxLevel = 4)
        val r = Finalizer.finalize(input(perfect), p)
        assertTrue(ItemType.RANDOM_BOX in r.itemsGained)
    }

    @Test fun productivityOnlyRewards() {
        val none = Finalizer.finalize(input(perfect), playerAt(10))
        val some = Finalizer.finalize(input(perfect.copy(productivityMinutes = 120)), playerAt(10))
        assertEquals(none.appliedDelta + 20, some.appliedDelta)
    }

    @Test fun customCriteriaCount() {
        val c = CustomCriterion(1, "Read pages", CustomType.AT_LEAST, 20.0, "pages", Stat.INT)
        val miss = Scoring.score(perfect, goals, listOf(c))
        assertFalse(miss.questCleared)
        val hit = Scoring.score(perfect.copy(custom = mapOf(1L to 25.0)), goals, listOf(c))
        assertTrue(hit.questCleared)
    }

    @Test fun phoneWindowClosesAtNoon() {
        val day = LocalDate.of(2026, 10, 18)
        assertTrue(DayClock.canEditEntries(day, day.atTime(23, 59)))
        assertFalse(DayClock.canEditEntries(day, day.plusDays(1).atTime(0, 0)))
        assertTrue(DayClock.canEditPhone(day, day.plusDays(1).atTime(11, 59)))
        assertFalse(DayClock.canEditPhone(day, day.plusDays(1).atTime(12, 0)))
        assertEquals(listOf(day), DayClock.daysToFinalize(day.minusDays(1), LocalDateTime.of(2026, 10, 19, 12, 0)))
        assertEquals(emptyList<LocalDate>(), DayClock.daysToFinalize(day.minusDays(1), LocalDateTime.of(2026, 10, 19, 11, 0)))
        assertEquals(3, DayClock.daysToFinalize(day.minusDays(3), LocalDateTime.of(2026, 10, 19, 13, 0)).size)
    }
}

class CodecTest {
    @Test fun weekGoalsReadOldSingleGoals() {
        val old = Codec.encodeGoals(Goals(steps = 9000))
        assertEquals(WeekGoals.uniform(Goals(steps = 9000)), Codec.decodeWeek(old))
        val week = WeekGoals.uniform(Goals()).with(5, Goals(steps = 500))
        assertEquals(week, Codec.decodeWeek(Codec.encodeWeek(week)))
        // 2026-10-17 is a Saturday.
        assertEquals(500, week.forEpochDay(java.time.LocalDate.of(2026, 10, 17).toEpochDay()).steps)
        assertEquals(8000, week.forEpochDay(java.time.LocalDate.of(2026, 10, 19).toEpochDay()).steps)
    }

    @Test fun playerRoundTrips() {
        val p = PlayerState(xp = 1234, maxLevel = 9, streak = 3, inventory = mapOf(ItemType.XP_BOOSTER to 2), statPoints = mapOf(Stat.STR to 1.5), titles = setOf(Title.PLAYER, Title.AWAKENED))
        assertEquals(p, Codec.decodePlayer(Codec.encodePlayer(p)))
        assertEquals(mapOf(1L to 2.5), Codec.decodeCustom(Codec.encodeCustom(mapOf(1L to 2.5))))
        assertEquals(Goals(kcal = 1900), Codec.decodeGoals(Codec.encodeGoals(Goals(kcal = 1900))))
    }
}
