package com.kiskujab.arise.core

/** Rewards granted outside a day's result (trials), and the notices that come with progress. */
object Progression {

    /** Adds XP, gold and items, with level, rank, title and unlock notices. Never lowers XP. */
    fun award(player: PlayerState, xp: Int, gold: Int, items: List<ItemType> = emptyList()): Pair<PlayerState, List<SystemEvent>> {
        val events = mutableListOf<SystemEvent>()
        val levelBefore = player.level
        val rankBefore = player.rank
        var p = player.copy(
            xp = player.xp + xp.coerceAtLeast(0),
            gold = player.gold + gold.coerceAtLeast(0),
            counters = player.counters.copy(
                goldEarned = player.counters.goldEarned + gold.coerceAtLeast(0),
                itemsFound = player.counters.itemsFound + items.size,
            ),
        )
        for (item in items) {
            p = p.withItem(item, 1)
            events += SystemEvent(EventType.ITEM, "Item Acquired: ${item.displayName}", "[${item.rarity.label}] ${item.description}")
        }
        val levelAfter = p.level
        if (levelAfter > levelBefore) events += SystemEvent(EventType.LEVEL_UP, "Level Up!", "You have reached level $levelAfter.")
        if (p.rank > rankBefore) events += SystemEvent(EventType.RANK_UP, "Rank Up!", "You are now ${p.rank.title}.")
        var recovered = false
        p = when {
            levelAfter > p.maxLevel -> p.copy(maxLevel = levelAfter, recoveryTarget = null).also { recovered = p.recoveryTarget != null }
            p.recoveryTarget != null && levelAfter >= p.recoveryTarget!! -> p.copy(recoveryTarget = null).also { recovered = true }
            else -> p
        }
        val (withTitles, titleEvents) = titles(p, recovered)
        events += titleEvents
        val (withUnlocks, unlockEvents) = unlocks(withTitles)
        events += unlockEvents
        return withUnlocks to events
    }

    fun titles(player: PlayerState, recovered: Boolean = false): Pair<PlayerState, List<SystemEvent>> {
        val earned = Title.earned(player) + (if (recovered) setOf(Title.RISEN) else emptySet())
        val new = earned - player.titles
        return player.copy(titles = player.titles + new) to new.map { SystemEvent(EventType.TITLE, "Title Acquired: ${it.displayName}", it.description) }
    }

    /** Announces runes and equipment that became available since the last check. */
    fun unlocks(player: PlayerState): Pair<PlayerState, List<SystemEvent>> {
        val events = mutableListOf<SystemEvent>()
        val known = player.knownUnlocks.toMutableSet()
        for (r in player.unlockedRunes()) {
            if (known.add("rune:${r.name}")) {
                events += SystemEvent(EventType.UNLOCK, "Rune Unlocked: ${r.displayName}", "[${r.slot.label}] ${r.description}\nEquip it on your rune page.")
            }
        }
        for (e in player.unlockedEquipment()) {
            if (known.add("item:${e.name}")) {
                events += SystemEvent(
                    EventType.UNLOCK, "New in the Armory: ${e.displayName}",
                    "[${e.rarity.label}] ${e.description}\nAvailable for ${e.price} gold.",
                )
            }
        }
        return player.copy(knownUnlocks = known) to events
    }
}
