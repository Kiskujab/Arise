package com.kiskujab.arise.core

import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** Every tunable number of the game in one place. */
object Rules {
    // Weights of the built-in criteria (perfect day = 100 base XP regardless of the total).
    const val W_KCAL = 20
    const val W_PROTEIN = 10
    const val W_MOVEMENT = 20
    const val W_SCREEN = 12
    const val W_DISTRACTION = 8
    const val W_BANDS = 12
    const val W_BRUSHING = 10
    const val W_SKINCARE = 8
    const val CUSTOM_DEFAULT_WEIGHT = 10

    // Calories: small tolerance, then an exponential penalty.
    const val KCAL_TOLERANCE = 50
    const val KCAL_ZERO_QUALITY_OVER = 500.0
    const val KCAL_PENALTY_SCALE = 15.0
    const val KCAL_PENALTY_DIVISOR = 250.0
    /** Overshoot used when calories were never reported, and the overshoot cap. */
    const val KCAL_OVER_CAP = 1500

    /** Exponential calorie penalty (before the level factor). 200 over ≈ 12, 600 over ≈ 120, 1000 over ≈ 655. */
    fun kcalPenalty(over: Int): Double {
        val effective = (min(over, KCAL_OVER_CAP) - KCAL_TOLERANCE).coerceAtLeast(0)
        if (effective == 0) return 0.0
        return KCAL_PENALTY_SCALE * (exp(effective / KCAL_PENALTY_DIVISOR) - 1.0)
    }

    // Bonuses (never penalised).
    const val PRODUCTIVITY_MINUTES_PER_XP = 6.0
    const val PRODUCTIVITY_MAX_XP = 30.0
    const val BAND_BONUS_PER_HOUR = 3.0
    const val BAND_MAX_HOURS = 24.0
    const val MOVEMENT_BONUS_MAX = 15.0

    // Streak multiplier on positive days: +0.5% per streak day, max +25%.
    fun streakBonus(streak: Int): Double = min(streak * 0.005, 0.25)

    /** Losses grow with level: level 1 → ×1, level 15 → ×1.7, level 60 → ×3.95. */
    fun levelFactor(level: Int): Double = 1.0 + (level - 1) / 20.0

    const val VACATION_MULTIPLIER = 0.5
    const val PENALTY_CLEARED_XP = 25
    const val PENALTY_FAILED_XP = 30 // × level factor

    // Leveling.
    const val MAX_LEVEL = 120
    const val SAFE_ZONE_STEP = 5
    private const val CURVE_A = 46.0
    private const val CURVE_P = 1.6

    /** Total XP needed to be at [level]. */
    fun xpForLevel(level: Int): Long {
        val l = level.coerceIn(1, MAX_LEVEL)
        return (CURVE_A * (l - 1).toDouble().pow(CURVE_P)).roundToLong()
    }

    fun levelForXp(xp: Long): Int {
        var level = 1
        while (level < MAX_LEVEL && xpForLevel(level + 1) <= xp) level++
        return level
    }

    /** The level you can never fall below once you've reached [level]: 5, 10, 15, ... */
    fun safeFloor(level: Int): Int = maxOf(1, (level / SAFE_ZONE_STEP) * SAFE_ZONE_STEP)

    // Items.
    const val FREEZE_EVERY_STREAK_DAYS = 7
    const val MAX_FREEZES = 5
    const val BASE_DROP_CHANCE = 0.08
    const val DROP_CHANCE_PER_STREAK_DAY = 0.001
    const val MAX_DROP_CHANCE = 0.15

    // Vacation leniency.
    fun vacationGoals(g: Goals) = g.copy(
        kcal = (g.kcal * 1.15).toInt(),
        steps = g.steps / 2,
        activeKcal = g.activeKcal / 2,
        screenMinutes = (g.screenMinutes * 1.5).toInt(),
        distractionPct = (g.distractionPct + 15).coerceAtMost(100),
        bandHours = (g.bandHours - 4.0).coerceAtLeast(12.0),
    )

    // Phone usage for a day can be reported until this hour of the next day.
    const val PHONE_REPORT_DEADLINE_HOUR = 12
}
