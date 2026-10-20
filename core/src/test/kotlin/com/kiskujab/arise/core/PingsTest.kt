package com.kiskujab.arise.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PingsTest {

    private val ctx = PingContext(
        hour = 14, level = 7, xp = Rules.xpForLevel(7) + 10, questStreak = 4, loginStreak = 6,
        gold = 500, armory = listOf("Thorn Plate" to 900),
        trials = listOf(Triple("Iron Ration", "3-Day Trial", 2)),
    )

    @Test fun pingsNeverReportQuestProgress() {
        val progressLike = Regex("""\d+\s*/\s*\d+|quests? complete|logged""", RegexOption.IGNORE_CASE)
        for (p in Pings.candidates(ctx.copy(penalty = listOf("Eat at most 1900 kcal")))) {
            assertFalse("${p.title} — ${p.text}", progressLike.containsMatchIn(p.title + " " + p.text))
        }
    }

    @Test fun recentKindsAreAvoided() {
        val r = Random(3)
        repeat(50) {
            val p = Pings.compose(ctx, listOf(PingKind.QUOTE, PingKind.NUDGE), emptySet(), r)
            assertNotEquals(PingKind.QUOTE, p.kind)
            assertNotEquals(PingKind.NUDGE, p.kind)
        }
    }

    @Test fun recentLinesAreAvoided() {
        val all = Pings.candidates(ctx)
        val keep = all.first()
        val p = Pings.compose(ctx, emptyList(), all.map { it.key }.toSet() - keep.key, Random(1))
        assertEquals(keep.key, p.key)
    }

    @Test fun manyPingsAreVaried() {
        val r = Random(9)
        val kinds = mutableListOf<PingKind>()
        val keys = mutableListOf<String>()
        repeat(12) {
            val p = Pings.compose(ctx, kinds, keys.takeLast(10).toSet(), r)
            kinds += p.kind
            keys += p.key
        }
        assertTrue(kinds.distinct().size >= 5)
        assertEquals(keys.size, keys.distinct().size)
        assertTrue(kinds.zipWithNext().none { (a, b) -> a == b })
    }

    @Test fun riskyLoginStreakGetsAWarning() {
        val risky = Pings.candidates(ctx.copy(loginAtRisk = true, activeToday = false))
        assertTrue(risky.any { it.key == "s:risk" })
        val safe = Pings.candidates(ctx.copy(loginAtRisk = false, activeToday = true))
        assertTrue(safe.none { it.key == "s:risk" })
    }

    @Test fun armoryPointsAtWhatCanBeBought() {
        val rich = Pings.candidates(ctx.copy(gold = 1000)).first { it.kind == PingKind.ARMORY }
        assertTrue(rich.text.contains("enough for Thorn Plate"))
        val poor = Pings.candidates(ctx).first { it.kind == PingKind.ARMORY }
        assertTrue(poor.text.startsWith("400 more gold"))
    }
}
