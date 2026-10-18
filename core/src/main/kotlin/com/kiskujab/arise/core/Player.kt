package com.kiskujab.arise.core

import kotlinx.serialization.Serializable

/** Everything about the player that changes when a day is finalized. */
@Serializable
data class PlayerState(
    val xp: Long = 0,
    val maxLevel: Int = 1,
    /** Set when the player falls below their best level; cleared (with a title) when they climb back. */
    val recoveryTarget: Int? = null,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val inventory: Map<ItemType, Int> = emptyMap(),
    val statPoints: Map<Stat, Double> = emptyMap(),
    val counters: Counters = Counters(),
    val titles: Set<Title> = setOf(Title.PLAYER),
) {
    val level: Int get() = Rules.levelForXp(xp)
    val rank: Rank get() = Rank.forLevel(level)

    fun count(item: ItemType): Int = inventory[item] ?: 0

    fun withItem(item: ItemType, delta: Int): PlayerState {
        val n = (count(item) + delta).coerceAtLeast(0)
        val capped = if (item == ItemType.STREAK_FREEZE) n.coerceAtMost(Rules.MAX_FREEZES) else n
        return copy(inventory = inventory + (item to capped))
    }

    fun statValue(stat: Stat): Int = (BASE_STAT + (statPoints[stat] ?: 0.0)).toInt().coerceAtLeast(1)

    companion object {
        const val BASE_STAT = 10
    }
}

/** Lifetime counters used for titles. */
@Serializable
data class Counters(
    val daysTracked: Int = 0,
    val daysAbsent: Int = 0,
    val vacationDays: Int = 0,
    val questClears: Int = 0,
    val perfectDays: Int = 0,
    val penaltiesCleared: Int = 0,
    val penaltiesFailed: Int = 0,
    val kcalDays: Int = 0,
    val movementDays: Int = 0,
    val bandDays: Int = 0,
    val brushDays: Int = 0,
    val skinDays: Int = 0,
    val focusDays: Int = 0,
    val totalSteps: Long = 0,
    val productivityMinutes: Long = 0,
    val itemsFound: Int = 0,
)

enum class Title(val displayName: String, val description: String) {
    PLAYER("Player", "Accepted the System."),
    AWAKENED("The Awakened", "Clear your first Daily Quest."),
    WOLF_SLAYER("Wolf Slayer", "Reach a 7-day streak."),
    IRON_WILL("Iron Will", "Reach a 30-day streak."),
    UNBREAKABLE("Unbreakable", "Reach a 100-day streak."),
    SOVEREIGN("Sovereign of Discipline", "Reach a 365-day streak."),
    OVERCOMER("One Who Overcame Adversity", "Clear a Penalty Quest."),
    RISEN("Fallen but Risen", "Climb back to your highest level after falling."),
    IRON_JAW("Iron Jaw", "Meet your rubber band goal on 30 days."),
    PEARL_SMILE("Pearl Smile", "Meet your brushing goal on 60 days."),
    GLASS_SKIN("Glass Skin", "Do your evening skincare on 30 days."),
    DISCIPLINED_EATER("Disciplined Eater", "Stay within your calorie limit on 30 days."),
    DIGITAL_MONK("Digital Monk", "Keep screen time and distraction in check on 30 days."),
    WANDERER("Wanderer", "Walk 1,000,000 steps in total."),
    SCHOLAR("Scholar", "Spend 100 hours on self-development."),
    FLAWLESS("Flawless", "Earn 10 S-grade days."),
    TREASURE_HUNTER("Treasure Hunter", "Find 10 items."),
    D_RANK("D-Rank Hunter", "Reach rank D."),
    C_RANK("C-Rank Hunter", "Reach rank C."),
    B_RANK("B-Rank Hunter", "Reach rank B."),
    A_RANK("A-Rank Hunter", "Reach rank A."),
    S_RANK("S-Rank Hunter", "Reach rank S."),
    S_PLUS_RANK("S+ Rank Hunter", "Reach rank S+."),
    MYTHICAL("Mythical", "Reach the Mythical rank."),
    GRANDMASTER("Grandmaster", "Reach the Grandmaster rank."),
    CHALLENGER("Challenger", "Reach the Challenger rank."),
    ;

    companion object {
        fun forRank(rank: Rank): Title? = when (rank) {
            Rank.E -> null
            Rank.D -> D_RANK
            Rank.C -> C_RANK
            Rank.B -> B_RANK
            Rank.A -> A_RANK
            Rank.S -> S_RANK
            Rank.S_PLUS -> S_PLUS_RANK
            Rank.MYTHICAL -> MYTHICAL
            Rank.GRANDMASTER -> GRANDMASTER
            Rank.CHALLENGER -> CHALLENGER
        }

        /** Titles whose conditions are met by [p] (rank and recovery titles are granted elsewhere). */
        fun earned(p: PlayerState): Set<Title> = buildSet {
            val c = p.counters
            add(PLAYER)
            if (c.questClears >= 1) add(AWAKENED)
            if (p.bestStreak >= 7) add(WOLF_SLAYER)
            if (p.bestStreak >= 30) add(IRON_WILL)
            if (p.bestStreak >= 100) add(UNBREAKABLE)
            if (p.bestStreak >= 365) add(SOVEREIGN)
            if (c.penaltiesCleared >= 1) add(OVERCOMER)
            if (c.bandDays >= 30) add(IRON_JAW)
            if (c.brushDays >= 60) add(PEARL_SMILE)
            if (c.skinDays >= 30) add(GLASS_SKIN)
            if (c.kcalDays >= 30) add(DISCIPLINED_EATER)
            if (c.focusDays >= 30) add(DIGITAL_MONK)
            if (c.totalSteps >= 1_000_000) add(WANDERER)
            if (c.productivityMinutes >= 100 * 60) add(SCHOLAR)
            if (c.perfectDays >= 10) add(FLAWLESS)
            if (c.itemsFound >= 10) add(TREASURE_HUNTER)
            Rank.entries.filter { it.minLevel <= p.maxLevel }.mapNotNullTo(this) { forRank(it) }
        }
    }
}
