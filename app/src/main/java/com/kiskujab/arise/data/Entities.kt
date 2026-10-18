package com.kiskujab.arise.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kiskujab.arise.core.Codec
import com.kiskujab.arise.core.CustomCriterion
import com.kiskujab.arise.core.CustomType
import com.kiskujab.arise.core.DayModifiers
import com.kiskujab.arise.core.DayValues
import com.kiskujab.arise.core.PenaltyQuest
import com.kiskujab.arise.core.PlayerState
import com.kiskujab.arise.core.Stat

/** The single player row. Game progress lives in [stateJson] (a [PlayerState]). */
@Entity(tableName = "player")
data class PlayerEntity(
    @PrimaryKey val id: Int = 0,
    val name: String,
    val awakenedEpochDay: Long,
    val awakenedAt: Long,
    val seed: Long,
    val lastFinalizedDay: Long,
    val stateJson: String,
    val activeTitle: String,
    /** Latest wall-clock time ever seen; used to detect the clock being turned back. */
    val maxSeenTime: Long,
) {
    val state: PlayerState get() = Codec.decodePlayer(stateJson)
}

@Entity(tableName = "days")
data class DayEntity(
    @PrimaryKey val epochDay: Long,
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
    val customJson: String? = null,
    /** Penalty quest assigned to this day (encoded [PenaltyQuest]). */
    val penalty: String? = null,
    val booster: Boolean = false,
    val shield: Boolean = false,
    val skipDay: Boolean = false,
    val finalized: Boolean = false,
    val absent: Boolean = false,
    val vacation: Boolean = false,
    val goalsJson: String? = null,
    val xpDelta: Int = 0,
    val grade: String? = null,
    val questCleared: Boolean = false,
    val levelAfter: Int = 0,
    val resultJson: String? = null,
    val finalizedAt: Long? = null,
) {
    val values: DayValues
        get() = DayValues(
            kcal = kcal, protein = protein, steps = steps, activeKcal = activeKcal,
            screenMinutes = screenMinutes, distractionPct = distractionPct, bandHours = bandHours,
            brushCount = brushCount, skincare = skincare, productivityMinutes = productivityMinutes,
            custom = Codec.decodeCustom(customJson),
        )

    val penaltyQuest: PenaltyQuest? get() = PenaltyQuest.decode(penalty)

    val modifiers: DayModifiers get() = DayModifiers(booster = booster, shield = shield, skipDay = skipDay)

    /** Nothing at all was reported. */
    val isEmpty: Boolean
        get() = kcal == null && protein == null && steps == null && activeKcal == null &&
            screenMinutes == null && distractionPct == null && bandHours == null && brushCount == null &&
            skincare == null && productivityMinutes == null && Codec.decodeCustom(customJson).isEmpty()
}

/** Goals take effect from [effectiveFrom] (epoch day). History keeps every version. */
@Entity(tableName = "goals")
data class GoalsEntity(
    @PrimaryKey val effectiveFrom: Long,
    val goalsJson: String,
)

/** A vacation from [startDay] to [endDay] inclusive (open-ended when null). */
@Entity(tableName = "vacations")
data class VacationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDay: Long,
    val endDay: Long? = null,
) {
    fun covers(day: Long) = day >= startDay && (endDay == null || day <= endDay)
}

/** Active from [createdDay] until the day before [archivedDay]. */
@Entity(tableName = "custom_criteria")
data class CustomCriterionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val target: Double,
    val unit: String,
    val stat: String,
    val weight: Int,
    val createdDay: Long,
    val archivedDay: Long? = null,
) {
    fun activeOn(day: Long) = day >= createdDay && (archivedDay == null || day < archivedDay)

    fun toCore() = CustomCriterion(
        id = id, name = name, type = CustomType.valueOf(type), target = target, unit = unit,
        stat = Stat.valueOf(stat), weight = weight,
    )
}

/** System messages: shown once as pop-ups, kept forever as the System log. */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val epochDay: Long,
    val type: String,
    val title: String,
    val message: String,
    val seen: Boolean = false,
)
