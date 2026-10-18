package com.kiskujab.arise.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** What gets stored for a finalized day, for the log screen. */
@Serializable
data class DayResultRecord(
    val xpLines: List<XpLine>,
    val results: List<CriterionResult>,
    val kcalOver: Int,
    val quality: Double,
)

/** JSON storage helpers. Unknown keys are ignored so older records stay readable. */
object Codec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val customMap = MapSerializer(Long.serializer(), Double.serializer())

    fun encodePlayer(p: PlayerState): String = json.encodeToString(PlayerState.serializer(), p)
    fun decodePlayer(s: String): PlayerState = json.decodeFromString(PlayerState.serializer(), s)

    fun encodeGoals(g: Goals): String = json.encodeToString(Goals.serializer(), g)
    fun decodeGoals(s: String): Goals = json.decodeFromString(Goals.serializer(), s)

    fun encodeWeek(w: WeekGoals): String = json.encodeToString(WeekGoals.serializer(), w)

    /** Reads week goals; older records stored a single [Goals] used for every day. */
    fun decodeWeek(s: String): WeekGoals =
        if (s.contains("\"days\"")) json.decodeFromString(WeekGoals.serializer(), s) else WeekGoals.uniform(decodeGoals(s))

    fun encodeCustom(m: Map<Long, Double>): String = json.encodeToString(customMap, m)
    fun decodeCustom(s: String?): Map<Long, Double> =
        if (s.isNullOrBlank()) emptyMap() else json.decodeFromString(customMap, s)

    fun encodeResult(r: DayResultRecord): String = json.encodeToString(DayResultRecord.serializer(), r)
    fun decodeResult(s: String?): DayResultRecord? =
        s?.let { runCatching { json.decodeFromString(DayResultRecord.serializer(), it) }.getOrNull() }
}
