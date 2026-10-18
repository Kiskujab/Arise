package com.kiskujab.arise.core

import kotlinx.serialization.Serializable

/** Player attributes. Each tracked criterion feeds one of them. */
enum class Stat(val full: String) {
    STR("Strength"),
    VIT("Vitality"),
    AGI("Agility"),
    INT("Intelligence"),
    PER("Perception"),
    CHA("Charisma"),
}

/** The player's targets. A snapshot is taken for every day so history never changes. */
@Serializable
data class Goals(
    val kcal: Int = 2200,
    val protein: Int = 150,
    val steps: Int = 8000,
    val activeKcal: Int = 300,
    val screenMinutes: Int = 180,
    val distractionPct: Int = 20,
    val bandHours: Double = 20.0,
    val brushCount: Int = 2,
)

/** What the player reported for a day. `null` means "not reported". */
data class DayValues(
    val kcal: Int? = null,
    val protein: Int? = null,
    val steps: Int? = null,
    val activeKcal: Int? = null,
    val screenMinutes: Int? = null,
    val distractionPct: Int? = null,
    val bandHours: Double? = null,
    val brushCount: Int? = null,
    val skincare: Boolean? = null,
    val productivityMinutes: Int? = null,
    val custom: Map<Long, Double> = emptyMap(),
)

enum class CustomType(val label: String) {
    CHECK("Yes / No"),
    AT_LEAST("At least"),
    AT_MOST("At most"),
}

/** A user-defined criterion. */
data class CustomCriterion(
    val id: Long,
    val name: String,
    val type: CustomType,
    val target: Double,
    val unit: String,
    val stat: Stat,
    val weight: Int = Rules.CUSTOM_DEFAULT_WEIGHT,
)

/** Keys of the built-in criteria. Custom ones use "custom:<id>". */
object Keys {
    const val KCAL = "kcal"
    const val PROTEIN = "protein"
    const val MOVEMENT = "movement"
    const val SCREEN = "screen"
    const val DISTRACTION = "distraction"
    const val BANDS = "bands"
    const val BRUSHING = "brushing"
    const val SKINCARE = "skincare"
    fun custom(id: Long) = "custom:$id"
    fun customId(key: String): Long? = key.removePrefix("custom:").takeIf { key.startsWith("custom:") }?.toLongOrNull()
}

enum class Grade(val label: String) { S("S"), A("A"), B("B"), C("C"), D("D"), E("E"), F("F") }
