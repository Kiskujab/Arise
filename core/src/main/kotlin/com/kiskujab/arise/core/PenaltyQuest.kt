package com.kiskujab.arise.core

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A corrective quest for the day after a failed one. It only tightens what went wrong, by bounded
 * amounts, so it always stays achievable. Criteria that can't be tightened further (or are yes/no)
 * go into [required]: they simply have to be met.
 */
data class PenaltyQuest(
    val kcalCut: Int = 0,
    val movementBoostPct: Int = 0,
    val screenCutPct: Int = 0,
    val distractionCut: Int = 0,
    val bandExtraHours: Double = 0.0,
    val brushExtra: Int = 0,
    val customTargets: Map<Long, Double> = emptyMap(),
    val required: Set<String> = emptySet(),
) {
    val keys: Set<String>
        get() = buildSet {
            if (kcalCut > 0) add(Keys.KCAL)
            if (movementBoostPct > 0) add(Keys.MOVEMENT)
            if (screenCutPct > 0) add(Keys.SCREEN)
            if (distractionCut > 0) add(Keys.DISTRACTION)
            if (bandExtraHours > 0) add(Keys.BANDS)
            if (brushExtra > 0) add(Keys.BRUSHING)
            customTargets.keys.forEach { add(Keys.custom(it)) }
            addAll(required)
        }

    val isEmpty: Boolean get() = keys.isEmpty()

    fun applyTo(g: Goals): Goals = g.copy(
        kcal = g.kcal - kcalCut,
        steps = (g.steps * (100 + movementBoostPct) / 100.0).roundToInt(),
        activeKcal = (g.activeKcal * (100 + movementBoostPct) / 100.0).roundToInt(),
        screenMinutes = max(MIN_SCREEN_MINUTES, (g.screenMinutes * (100 - screenCutPct) / 100.0).roundToInt()),
        distractionPct = max(MIN_DISTRACTION, g.distractionPct - distractionCut),
        bandHours = (g.bandHours + bandExtraHours).coerceAtMost(MAX_BAND_HOURS),
        brushCount = (g.brushCount + brushExtra).coerceAtMost(MAX_BRUSH),
    )

    /** Human readable lines for the System window. */
    fun describe(customs: List<CustomCriterion> = emptyList()): List<String> = buildList {
        if (kcalCut > 0) add("Calorie limit reduced by $kcalCut kcal")
        if (movementBoostPct > 0) add("Steps and active kcal goals +$movementBoostPct%")
        if (screenCutPct > 0) add("Screen time limit −$screenCutPct%")
        if (distractionCut > 0) add("Distraction limit −$distractionCut pts")
        if (bandExtraHours > 0) add("Wear rubber bands +${fmt(bandExtraHours)} h")
        if (brushExtra > 0) add("Brush teeth one extra time")
        for ((id, t) in customTargets) {
            val c = customs.find { it.id == id } ?: continue
            add("${c.name}: target tightened to ${fmt(t)} ${c.unit}".trim())
        }
        for (key in required) {
            add(
                when (key) {
                    Keys.PROTEIN -> "Reach your protein goal"
                    Keys.SKINCARE -> "Do not skip evening skincare"
                    Keys.BANDS -> "Meet your rubber band goal"
                    Keys.BRUSHING -> "Meet your brushing goal"
                    Keys.KCAL -> "Stay under your calorie limit"
                    else -> customs.find { Keys.custom(it.id) == key }?.let { "Complete: ${it.name}" } ?: "Complete the failed quest"
                },
            )
        }
    }

    /** Compact storage format: key=value pairs separated by ';'. */
    fun encode(): String = buildList {
        if (kcalCut > 0) add("kcal=$kcalCut")
        if (movementBoostPct > 0) add("move=$movementBoostPct")
        if (screenCutPct > 0) add("screen=$screenCutPct")
        if (distractionCut > 0) add("dist=$distractionCut")
        if (bandExtraHours > 0) add("bands=$bandExtraHours")
        if (brushExtra > 0) add("brush=$brushExtra")
        customTargets.forEach { (id, t) -> add("c$id=$t") }
        if (required.isNotEmpty()) add("req=" + required.sorted().joinToString(","))
    }.joinToString(";")

    companion object {
        const val MAX_KCAL_CUT = 400
        const val MIN_KCAL_CUT = 100
        const val MAX_BAND_HOURS = 22.0
        const val MAX_BRUSH = 3
        const val MIN_SCREEN_MINUTES = 30
        const val MIN_DISTRACTION = 5

        fun decode(s: String?): PenaltyQuest? {
            if (s.isNullOrBlank()) return null
            var p = PenaltyQuest()
            val customs = mutableMapOf<Long, Double>()
            for (part in s.split(';')) {
                val kv = part.split('=', limit = 2)
                if (kv.size != 2) continue
                val (k, v) = kv
                p = when (k) {
                    "kcal" -> p.copy(kcalCut = v.toInt())
                    "move" -> p.copy(movementBoostPct = v.toInt())
                    "screen" -> p.copy(screenCutPct = v.toInt())
                    "dist" -> p.copy(distractionCut = v.toInt())
                    "bands" -> p.copy(bandExtraHours = v.toDouble())
                    "brush" -> p.copy(brushExtra = v.toInt())
                    "req" -> p.copy(required = v.split(',').filter { it.isNotBlank() }.toSet())
                    else -> {
                        k.removePrefix("c").toLongOrNull()?.let { customs[it] = v.toDouble() }
                        p
                    }
                }
            }
            return p.copy(customTargets = customs).takeUnless { it.isEmpty }
        }

        /**
         * Builds tomorrow's corrective quest from today's failures. [baseGoals] are tomorrow's
         * normal goals; corrections are capped so the target always stays reachable.
         */
        fun fromFailures(score: DayScore, customs: List<CustomCriterion>, baseGoals: Goals): PenaltyQuest? {
            val failed = score.failedKeys
            if (failed.isEmpty()) return null
            val required = mutableSetOf<String>()
            val customTargets = mutableMapOf<Long, Double>()
            for (c in customs) {
                val key = Keys.custom(c.id)
                if (key !in failed) continue
                when (c.type) {
                    CustomType.AT_LEAST -> customTargets[c.id] = c.target * 1.1
                    CustomType.AT_MOST -> customTargets[c.id] = c.target * 0.9
                    CustomType.CHECK -> required += key
                }
            }
            val bandExtra = if (Keys.BANDS in failed) (MAX_BAND_HOURS - baseGoals.bandHours).coerceIn(0.0, 1.0) else 0.0
            if (Keys.BANDS in failed && bandExtra == 0.0) required += Keys.BANDS
            val brushExtra = if (Keys.BRUSHING in failed) (MAX_BRUSH - baseGoals.brushCount).coerceIn(0, 1) else 0
            if (Keys.BRUSHING in failed && brushExtra == 0) required += Keys.BRUSHING
            if (Keys.PROTEIN in failed) required += Keys.PROTEIN
            if (Keys.SKINCARE in failed) required += Keys.SKINCARE

            val q = PenaltyQuest(
                kcalCut = if (Keys.KCAL in failed) (score.kcalOver / 2).coerceIn(MIN_KCAL_CUT, MAX_KCAL_CUT) else 0,
                movementBoostPct = if (Keys.MOVEMENT in failed) 15 else 0,
                screenCutPct = if (Keys.SCREEN in failed) 20 else 0,
                distractionCut = if (Keys.DISTRACTION in failed) 5 else 0,
                bandExtraHours = bandExtra,
                brushExtra = brushExtra,
                customTargets = customTargets,
                required = required,
            )
            return q.takeUnless { it.isEmpty }
        }

        private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(java.util.Locale.US, "%.1f", d)
    }
}
