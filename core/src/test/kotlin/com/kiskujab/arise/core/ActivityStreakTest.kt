package com.kiskujab.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityStreakTest {

    @Test fun countsConsecutiveActiveDays() {
        val s = ActivityStreak().mark(100).mark(100).mark(101).mark(102)
        assertEquals(3, s.current)
        assertEquals(3, s.best)
        assertTrue(s.activeOn(102))
    }

    @Test fun gapRestartsButKeepsBest() {
        val s = ActivityStreak().mark(100).mark(101).mark(102).mark(105)
        assertEquals(1, s.current)
        assertEquals(3, s.best)
    }

    @Test fun shownStreakSurvivesUntilTheEndOfTheNextDay() {
        val s = ActivityStreak().mark(100).mark(101)
        assertEquals(2, s.shown(101))
        assertEquals(2, s.shown(102))
        assertTrue(s.atRisk(102))
        assertFalse(s.atRisk(101))
        assertEquals(0, s.shown(103))
    }

    @Test fun olderDayDoesNotRewind() {
        val s = ActivityStreak().mark(100).mark(101)
        assertEquals(s, s.mark(99))
    }

    @Test fun oldSavesLoadWithoutAStreak() {
        val p = Codec.decodePlayer("""{"xp":10}""")
        assertEquals(0, p.activity.current)
        assertEquals(0, p.activity.shown(20000))
    }
}
