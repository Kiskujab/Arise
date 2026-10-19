package com.kiskujab.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GearTest {

    private val goals = Goals()
    private val day = 20000L

    private val perfect = DayValues(
        kcal = 2100, protein = 160, steps = 8000, activeKcal = 300,
        screenMinutes = 120, distractionPct = 10, bandHours = 20.0, brushCount = 2, skincare = true,
    )

    private fun input(values: DayValues, epochDay: Long = day, absent: Boolean = false) = FinalizeInput(
        epochDay = epochDay, values = values, baseGoals = goals, penalty = null, customs = emptyList(),
        vacation = false, absent = absent, nextDayBaseGoals = goals, nextDayVacation = false, seed = 42,
    )

    private fun player(level: Int, loadout: Loadout = Loadout(), streak: Int = 0, owned: Set<Equipment> = emptySet()) =
        PlayerState(
            xp = Rules.xpForLevel(level), maxLevel = level, streak = streak, bestStreak = streak,
            equipment = owned, loadouts = listOf(DatedLoadout(0, loadout)),
        )

    @Test fun perfectDayEarnsGold() {
        val r = Finalizer.finalize(input(perfect), player(5))
        assertEquals(110, r.goldGained)
        assertEquals(110, r.player.gold)
        assertEquals(110L, r.player.counters.goldEarned)
    }

    @Test fun absentDayEarnsNoGoldAndGearDoesNothing() {
        val gear = Loadout(items = listOf(Equipment.THORN_PLATE))
        val r = Finalizer.finalize(input(DayValues(), absent = true), player(6, gear, owned = setOf(Equipment.THORN_PLATE)))
        assertEquals(0, r.goldGained)
        assertTrue(r.xpLines.none { it.label.startsWith("Blocked") })
    }

    @Test fun warlordsMailBlocksQuarterOfCalorieDamage() {
        val over = perfect.copy(kcal = goals.kcal + 600)
        val plain = Finalizer.finalize(input(over), player(13))
        val mail = Finalizer.finalize(
            input(over),
            player(13, Loadout(items = listOf(Equipment.WARLORDS_MAIL)), owned = setOf(Equipment.WARLORDS_MAIL)),
        )
        val kcalLoss = -plain.xpLines.first { it.label.startsWith("Calorie") }.amount
        val blocked = mail.xpLines.first { it.label.startsWith("Blocked") }.amount
        assertEquals(Math.round(kcalLoss * 0.25).toInt(), blocked)
        assertTrue(mail.appliedDelta > plain.appliedDelta)
    }

    @Test fun unownedEquipmentHasNoEffect() {
        val over = perfect.copy(kcal = goals.kcal + 600)
        val r = Finalizer.finalize(input(over), player(10, Loadout(items = listOf(Equipment.WARLORDS_MAIL))))
        assertTrue(r.xpLines.none { it.label.startsWith("Blocked") })
    }

    @Test fun keystoneNeedsLevelThree() {
        val oath = Loadout(keystone = Rune.CONQUERORS_OATH)
        val low = Finalizer.finalize(input(perfect), player(2, oath, streak = 10))
        val high = Finalizer.finalize(input(perfect), player(3, oath, streak = 10))
        assertTrue(low.xpLines.none { it.label == "Conqueror's Oath" })
        assertEquals(20, high.xpLines.first { it.label == "Conqueror's Oath" }.amount)
    }

    @Test fun loadoutChangesApplyFromTheirDay() {
        val p = player(10).withLoadout(day + 1, Loadout(items = listOf(Equipment.INITIATES_BLADE)))
            .copy(equipment = setOf(Equipment.INITIATES_BLADE))
        val today = Finalizer.finalize(input(perfect, day), p)
        assertTrue(today.xpLines.none { it.label == "Initiate's Blade" })
        val tomorrow = Finalizer.finalize(input(perfect, day + 1), today.player)
        assertEquals(3, tomorrow.xpLines.first { it.label == "Initiate's Blade" }.amount)
    }

    @Test fun newerLoadoutReplacesPendingOne() {
        val a = Loadout(items = listOf(Equipment.INITIATES_BLADE))
        val b = Loadout(items = listOf(Equipment.GOLDEN_IDOL))
        val p = PlayerState().withLoadout(day + 1, a).withLoadout(day + 1, b)
        assertEquals(b, p.loadoutFor(day + 1))
        assertEquals(Loadout(), p.loadoutFor(day))
        // A change made the next day keeps the previous version for the day before.
        val q = p.withLoadout(day + 2, a)
        assertEquals(b, q.loadoutFor(day + 1))
        assertEquals(a, q.loadoutFor(day + 2))
    }

    @Test fun hourglassNegatesALossThenCoolsDown() {
        val gear = Loadout(items = listOf(Equipment.HOURGLASS_OF_STASIS))
        val bad = perfect.copy(kcal = goals.kcal + 800, protein = 0, steps = 0, activeKcal = 0)
        val first = Finalizer.finalize(input(bad, day), player(12, gear, owned = setOf(Equipment.HOURGLASS_OF_STASIS)))
        assertEquals(0, first.appliedDelta)
        val second = Finalizer.finalize(input(bad, day + 1), first.player)
        assertTrue(second.appliedDelta < 0)
        val later = Finalizer.finalize(input(bad, day + 15), second.player)
        assertEquals(0, later.appliedDelta)
    }

    @Test fun guardiansHaloKeepsTheLevel() {
        val gear = Loadout(items = listOf(Equipment.GUARDIANS_HALO))
        val p = player(23, gear, owned = setOf(Equipment.GUARDIANS_HALO))
        val r = Finalizer.finalize(input(perfect.copy(kcal = goals.kcal + 1200)), p)
        assertEquals(23, r.levelAfter)
        val plain = Finalizer.finalize(input(perfect.copy(kcal = goals.kcal + 1200)), player(23))
        assertTrue(plain.levelAfter < 23)
    }

    @Test fun slotsOpenWithLevelAndRank() {
        assertFalse(Slots.keystoneOpen(2))
        assertTrue(Slots.keystoneOpen(3))
        assertEquals(0, Slots.minorSlots(4))
        assertEquals(3, Slots.minorSlots(25))
        assertEquals(1, Slots.itemSlots(1))
        assertEquals(2, Slots.itemSlots(10))
        assertEquals(6, Slots.itemSlots(115))
    }

    @Test fun unlocksAreAnnouncedOnce() {
        val r = Finalizer.finalize(input(perfect), player(3))
        assertTrue(r.events.any { it.type == EventType.UNLOCK && it.title.contains("Conqueror's Oath") })
        val again = Finalizer.finalize(input(perfect, day + 1), r.player)
        assertTrue(again.events.none { it.type == EventType.UNLOCK && it.title.contains("Conqueror's Oath") })
    }

    @Test fun oldPlayerJsonStillLoads() {
        val old = """{"xp":500,"maxLevel":4,"streak":3,"bestStreak":3,"inventory":{"XP_BOOSTER":1}}"""
        val p = Codec.decodePlayer(old)
        assertEquals(0L, p.gold)
        assertEquals(Loadout(), p.loadoutFor(day))
        val back = Codec.decodePlayer(Codec.encodePlayer(p.withLoadout(day, Loadout(keystone = Rune.TRIUMPH))))
        assertEquals(Rune.TRIUMPH, back.loadoutFor(day).keystone)
    }

    // ------------------------------------------------------------------ trials

    private val monday = LocalDate.of(2026, 10, 19).toEpochDay()

    @Test fun trialPeriods() {
        assertEquals(monday, Trials.weekStart(monday + 6))
        assertEquals(monday + 7, Trials.weekStart(monday + 7))
        assertEquals(monday, Trials.triadStart(monday + 2, monday))
        assertEquals(monday + 3, Trials.triadStart(monday + 3, monday))
        assertTrue(Trials.forPeriod(TrialSpan.RAID, monday + 3, monday + 1, 1, goals).isEmpty())
        val raid = Trials.forPeriod(TrialSpan.RAID, monday + 3, monday, 1, goals)
        assertEquals(2, raid.size)
        assertEquals(raid, Trials.forPeriod(TrialSpan.RAID, monday + 6, monday, 1, goals))
        assertTrue(Trials.forPeriod(TrialSpan.TRIAD, monday, monday, 1, goals.copy(steps = 0)).none { it.kind == TrialKind.STEPS })
    }

    private fun day(i: Int, status: TrialDayStatus, met: Boolean, steps: Int = 0, screen: Int? = null) = TrialDay(
        epochDay = monday + i, goals = goals, values = DayValues(steps = steps, screenMinutes = screen),
        results = listOf(CriterionResult(Keys.PROTEIN, "Protein", Stat.VIT, 10, if (met) 1.0 else 0.0, true, true)),
        status = status, cleared = met,
    )

    @Test fun countTrialProgress() {
        val t = Trial(TrialSpan.TRIAD, TrialKind.PROTEIN, monday)
        val live = Trials.progress(t, listOf(day(0, TrialDayStatus.FINAL, true), day(1, TrialDayStatus.LIVE, true), day(2, TrialDayStatus.FUTURE, false)))
        assertEquals(TrialState.ACTIVE, live.state)
        assertEquals(2.0, live.current, 0.0)
        val failed = Trials.progress(t, listOf(day(0, TrialDayStatus.FINAL, false), day(1, TrialDayStatus.LIVE, true), day(2, TrialDayStatus.FUTURE, false)))
        assertEquals(TrialState.FAILED, failed.state)
        val done = Trials.progress(t, (0..2).map { day(it, TrialDayStatus.FINAL, true) })
        assertEquals(TrialState.CLEARED, done.state)
    }

    @Test fun screenTrialCountsUnreportedDaysAsFull() {
        val t = Trial(TrialSpan.TRIAD, TrialKind.SCREEN, monday)
        val p = Trials.progress(t, listOf(day(0, TrialDayStatus.FINAL, true, screen = null), day(1, TrialDayStatus.LIVE, true), day(2, TrialDayStatus.FUTURE, false)))
        assertEquals(TrialState.FAILED, p.state)
    }

    @Test fun settleRewardsOnceAndRaidBossDropsBox() {
        val raid = listOf(Trial(TrialSpan.RAID, TrialKind.PROTEIN, monday), Trial(TrialSpan.RAID, TrialKind.UNBROKEN, monday))
        val days = (0..6).map { day(it, TrialDayStatus.FINAL, true) }
        val (p, events) = Trials.settle(PlayerState(), raid.map { it to days })
        assertEquals(200L, p.xp)
        assertEquals(800L, p.gold)
        assertEquals(1, p.count(ItemType.RANDOM_BOX))
        assertEquals(2, p.counters.trialsCleared)
        assertTrue(events.any { it.title == "Raid Boss Defeated" })
        val (again, none) = Trials.settle(p, raid.map { it to days })
        assertEquals(p, again)
        assertTrue(none.isEmpty())
    }
}
