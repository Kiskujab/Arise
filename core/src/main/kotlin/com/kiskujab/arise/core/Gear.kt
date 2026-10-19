package com.kiskujab.arise.core

import kotlinx.serialization.Serializable

/**
 * How something is unlocked: a counter the player raises by playing, and the value it has to
 * reach. [progress] returns the current value.
 */
class Unlock(val text: String, val target: Long, val progress: (PlayerState) -> Long) {
    fun isMet(p: PlayerState): Boolean = progress(p) >= target
    fun fraction(p: PlayerState): Float = if (target <= 0) 1f else (progress(p).toFloat() / target).coerceIn(0f, 1f)

    companion object {
        fun level(n: Int) = Unlock("Reach level $n", n.toLong()) { it.maxLevel.toLong() }
        fun streak(n: Int) = Unlock("Reach a $n-day streak", n.toLong()) { it.bestStreak.toLong() }
        fun clears(n: Int) = Unlock("Clear $n Daily Quests", n.toLong()) { it.counters.questClears.toLong() }
        fun perfect(n: Int) = Unlock("Earn $n S-grade days", n.toLong()) { it.counters.perfectDays.toLong() }
        fun penalties(n: Int) = Unlock("Clear $n Penalty Quests", n.toLong()) { it.counters.penaltiesCleared.toLong() }
        fun kcal(n: Int) = Unlock("Stay within calories on $n days", n.toLong()) { it.counters.kcalDays.toLong() }
        fun movement(n: Int) = Unlock("Meet the movement goal on $n days", n.toLong()) { it.counters.movementDays.toLong() }
        fun bands(n: Int) = Unlock("Meet the rubber band goal on $n days", n.toLong()) { it.counters.bandDays.toLong() }
        fun skin(n: Int) = Unlock("Do evening skincare on $n days", n.toLong()) { it.counters.skinDays.toLong() }
        fun focus(n: Int) = Unlock("Keep the phone in check on $n days", n.toLong()) { it.counters.focusDays.toLong() }
        fun study(hours: Int) = Unlock("Spend $hours h on self-development", hours * 60L) { it.counters.productivityMinutes }
        fun items(n: Int) = Unlock("Find $n items", n.toLong()) { it.counters.itemsFound.toLong() }
        fun trials(n: Int) = Unlock("Clear $n Trials", n.toLong()) { it.counters.trialsCleared.toLong() }
        fun days(n: Int) = Unlock("Report $n days", n.toLong()) { (it.counters.daysTracked - it.counters.daysAbsent).toLong() }
    }
}

enum class RuneSlot(val label: String) { KEYSTONE("Keystone"), MINOR("Minor") }

/** Passive perks. Free once unlocked; equipped on the rune page. */
enum class Rune(val displayName: String, val slot: RuneSlot, val description: String, val unlock: Unlock) {
    // --- Keystones (one at a time) ---
    CONQUERORS_OATH(
        "Conqueror's Oath", RuneSlot.KEYSTONE,
        "Every day of your streak adds +2% to quest XP, up to +20%.",
        Unlock.level(3),
    ),
    PHANTOM_HARVEST(
        "Phantom Harvest", RuneSlot.KEYSTONE,
        "Every S-grade day you ever earned is a harvested soul: +1% quest XP per soul, up to +15%.",
        Unlock.perfect(3),
    ),
    FLEET_STRIDE(
        "Fleet Stride", RuneSlot.KEYSTONE,
        "Movement over the goal gives double bonus XP.",
        Unlock.movement(10),
    ),
    GRASP_OF_THE_ETERNAL(
        "Grasp of the Eternal", RuneSlot.KEYSTONE,
        "On days you hit your protein goal: +6 XP and calorie overflow damage −25%.",
        Unlock.kcal(10),
    ),
    ARCANE_FOCUS(
        "Arcane Focus", RuneSlot.KEYSTONE,
        "Self-development XP is worth 25% more and its daily cap doubles to 60.",
        Unlock.study(10),
    ),
    UNDYING_AEGIS(
        "Undying Aegis", RuneSlot.KEYSTONE,
        "All damage you take is reduced by 20%.",
        Unlock.penalties(3),
    ),

    // --- Minor runes ---
    TRIUMPH("Triumph", RuneSlot.MINOR, "Clearing the Daily Quest gives +5 XP.", Unlock.clears(5)),
    SECOND_WIND("Second Wind", RuneSlot.MINOR, "Clearing a Penalty Quest gives +25 XP more.", Unlock.penalties(1)),
    IRON_PLATING("Iron Plating", RuneSlot.MINOR, "Calorie overflow damage −20%.", Unlock.kcal(7)),
    TREASURE_HUNTER("Treasure Hunter", RuneSlot.MINOR, "Item drop chance +4%.", Unlock.trials(2)),
    RISING_STORM("Rising Storm", RuneSlot.MINOR, "Cleared days give +1 XP for every 2 levels you have.", Unlock.level(10)),
    ABSOLUTE_DISCIPLINE("Absolute Discipline", RuneSlot.MINOR, "S-grade days give +8 XP.", Unlock.perfect(1)),
    OVERGROWTH("Overgrowth", RuneSlot.MINOR, "Gold income +15%.", Unlock.days(14)),
    UNFLINCHING("Unflinching", RuneSlot.MINOR, "Quest failure damage −15%.", Unlock.streak(7)),
    NIGHT_WARD("Night Ward", RuneSlot.MINOR, "Skincare and brushing both done: +4 XP.", Unlock.skin(7)),
    STEEL_JAW("Steel Jaw", RuneSlot.MINOR, "Rubber band overtime gives +50% bonus XP.", Unlock.bands(10)),
}

/** Gear bought with gold in the Armory once unlocked. Equipped in item slots. */
enum class Equipment(
    val displayName: String,
    val rarity: Rarity,
    val price: Int,
    val description: String,
    val unlock: Unlock,
) {
    INITIATES_BLADE("Initiate's Blade", Rarity.UNCOMMON, 300, "Cleared days give +3 XP.", Unlock.level(1)),
    GOLDEN_IDOL("Golden Idol", Rarity.UNCOMMON, 400, "Gold income +20%.", Unlock.level(2)),
    SWIFTSTRIDE_GREAVES("Swiftstride Greaves", Rarity.UNCOMMON, 600, "Movement goal met: +5 XP.", Unlock.movement(5)),
    THORN_PLATE("Thorn Plate", Rarity.RARE, 900, "Quest failure damage −15%.", Unlock.penalties(2)),
    WARLORDS_MAIL("Warlord's Mail", Rarity.RARE, 1200, "Calorie overflow damage −25%.", Unlock.kcal(15)),
    SOLAR_AEGIS("Solar Aegis", Rarity.RARE, 1300, "Penalty Quest failure damage −50%.", Unlock.level(8)),
    SOULSTEALER_TOME("Soulstealer Tome", Rarity.EPIC, 1600, "Cleared days give +1 XP per streak day (max +25).", Unlock.streak(10)),
    ARCHMAGES_COWL("Archmage's Cowl", Rarity.EPIC, 1800, "Self-development XP +50%.", Unlock.study(20)),
    EDGE_OF_INFINITY("Edge of Infinity", Rarity.EPIC, 2200, "S-grade days give +20% quest XP.", Unlock.perfect(5)),
    HOURGLASS_OF_STASIS(
        "Hourglass of Stasis", Rarity.EPIC, 2500,
        "Once every 14 days, a day that would cost XP costs nothing. Triggers automatically.",
        Unlock.level(10),
    ),
    TRINITY_SIGIL("Trinity Sigil", Rarity.EPIC, 2800, "Bands, brushing and skincare all met: +8 XP.", Unlock.bands(20)),
    GUARDIANS_HALO(
        "Guardian's Halo", Rarity.LEGENDARY, 3200,
        "Once every 30 days, stops you from losing a level. Triggers automatically.",
        Unlock.level(20),
    ),
    MONARCHS_CROWN("Monarch's Crown", Rarity.LEGENDARY, 6000, "All XP gains +10%.", Unlock.level(35)),
    ;

    companion object {
        const val HOURGLASS_COOLDOWN = 14L
        const val HALO_COOLDOWN = 30L
    }
}

/** What the player has equipped. */
@Serializable
data class Loadout(
    val keystone: Rune? = null,
    val minors: List<Rune> = emptyList(),
    val items: List<Equipment> = emptyList(),
) {
    val runes: List<Rune> get() = listOfNotNull(keystone) + minors

    fun has(r: Rune) = keystone == r || r in minors
    fun has(e: Equipment) = e in items

    /** Drops whatever no longer fits (e.g. after losing levels). */
    fun effective(p: PlayerState, level: Int = p.level): Loadout = Loadout(
        keystone = keystone?.takeIf { Slots.keystoneOpen(level) && it.slot == RuneSlot.KEYSTONE && it.unlock.isMet(p) },
        minors = minors.filter { it.slot == RuneSlot.MINOR && it.unlock.isMet(p) }.distinct().take(Slots.minorSlots(level)),
        items = items.filter { it in p.equipment }.distinct().take(Slots.itemSlots(level)),
    )
}

/** A loadout and the day it takes effect from. */
@Serializable
data class DatedLoadout(val from: Long, val loadout: Loadout)

object Slots {
    const val KEYSTONE_LEVEL = 3
    val MINOR_LEVELS = listOf(5, 12, 25)

    fun keystoneOpen(level: Int) = level >= KEYSTONE_LEVEL
    fun minorSlots(level: Int) = MINOR_LEVELS.count { level >= it }

    /** One item slot per rank reached, up to six (E 1 · D 2 · C 3 · B 4 · A 5 · S 6). */
    fun itemSlots(level: Int) = (Rank.forLevel(level).ordinal + 1).coerceAtMost(6)

    fun itemSlotLevel(index: Int): Int = Rank.entries[index.coerceIn(0, 5)].minLevel
}

/** Consumables for sale. Skip Day Tickets can only be found. */
object Shop {
    val prices: Map<ItemType, Int> = mapOf(
        ItemType.STREAK_FREEZE to 400,
        ItemType.RANDOM_BOX to 350,
        ItemType.XP_BOOSTER to 600,
        ItemType.PENALTY_SHIELD to 1500,
    )

    /** Gold for a finalized day before multipliers: the XP you kept, plus 10 for a cleared quest. */
    fun dayGold(appliedDelta: Int, cleared: Boolean, absent: Boolean): Int =
        if (absent) 0 else appliedDelta.coerceAtLeast(0) + if (cleared) 10 else 0
}
