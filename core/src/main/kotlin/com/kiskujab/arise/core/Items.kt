package com.kiskujab.arise.core

import kotlin.random.Random

enum class Rarity(val label: String, val color: Long) {
    UNCOMMON("Uncommon", 0xFF4CD964),
    RARE("Rare", 0xFF3DA9FC),
    EPIC("Epic", 0xFFB388FF),
    LEGENDARY("Legendary", 0xFFFFC857),
}

enum class ItemType(
    val displayName: String,
    val rarity: Rarity,
    val description: String,
    /** Relative drop weight. */
    val dropWeight: Int,
    /** Can be activated for today from the inventory. */
    val usable: Boolean,
) {
    STREAK_FREEZE(
        "Streak Freeze", Rarity.UNCOMMON,
        "Used automatically when your streak would break. The streak survives, frozen in time.",
        35, false,
    ),
    XP_BOOSTER(
        "XP Booster", Rarity.RARE,
        "Activate for today: all XP gained today is doubled. Losses are not affected.",
        25, true,
    ),
    RANDOM_BOX(
        "Random Box", Rarity.RARE,
        "Open it to receive a random item.",
        20, true,
    ),
    PENALTY_SHIELD(
        "Penalty Shield", Rarity.EPIC,
        "Activate for today: if today ends in an XP loss, the loss is negated.",
        12, true,
    ),
    SKIP_DAY(
        "Skip Day Ticket", Rarity.LEGENDARY,
        "Activate for today: every quest is marked as perfectly completed.",
        3, true,
    );

    companion object {
        fun roll(random: Random, exclude: Set<ItemType> = emptySet()): ItemType {
            val pool = entries.filter { it !in exclude }
            var r = random.nextInt(pool.sumOf { it.dropWeight })
            for (item in pool) {
                r -= item.dropWeight
                if (r < 0) return item
            }
            return pool.last()
        }

        fun dropChance(streak: Int): Double =
            (Rules.BASE_DROP_CHANCE + streak * Rules.DROP_CHANCE_PER_STREAK_DAY).coerceAtMost(Rules.MAX_DROP_CHANCE)
    }
}
