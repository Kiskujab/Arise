package com.kiskujab.arise.core

import java.util.Locale
import kotlin.random.Random

/**
 * The daytime pings. They never report quest progress — most values can only be logged in the
 * evening — and rotate between kinds so two pings in a row rarely feel the same.
 */
enum class PingKind { QUOTE, LORE, NUDGE, PROGRESS, STREAK, TRIAL, ARMORY, PENALTY }

data class Ping(val kind: PingKind, val title: String, val text: String, /** Identifies the line, to avoid repeats. */ val key: String)

/** What the System knows when it sends a ping. Everything optional is left out when unknown. */
data class PingContext(
    val hour: Int,
    val level: Int,
    val xp: Long,
    val questStreak: Int = 0,
    val loginStreak: Int = 0,
    val loginAtRisk: Boolean = false,
    val activeToday: Boolean = false,
    val gold: Long = 0,
    /** Unlocked equipment not owned yet, cheapest first: name to price. */
    val armory: List<Pair<String, Int>> = emptyList(),
    /** Active trials: title, period label, days left including today. */
    val trials: List<Triple<String, String, Int>> = emptyList(),
    val penalty: List<String> = emptyList(),
    val wearsBands: Boolean = true,
    val skincare: Boolean = true,
    val walks: Boolean = true,
    val vacation: Boolean = false,
)

object Pings {

    /**
     * Picks a ping. Kinds in [recentKinds] (newest last) and lines in [recentKeys] are avoided
     * while there is anything else to say.
     */
    fun compose(ctx: PingContext, recentKinds: List<PingKind>, recentKeys: Set<String>, random: Random = Random.Default): Ping {
        val all = candidates(ctx)
        val fresh = all.filter { it.key !in recentKeys }.ifEmpty { all }
        val avoid = recentKinds.takeLast(2).toSet()
        val pool = fresh.filter { it.kind !in avoid }.ifEmpty { fresh }
        // One kind first (so a long list of quotes doesn't drown the rest), then a line of it.
        val kinds = pool.map { it.kind }.distinct()
        val weights = kinds.map { WEIGHTS[it] ?: 1 }
        var roll = random.nextInt(weights.sum())
        var kind = kinds.first()
        for ((i, k) in kinds.withIndex()) {
            if (roll < weights[i]) { kind = k; break }
            roll -= weights[i]
        }
        val lines = pool.filter { it.kind == kind }
        return lines[random.nextInt(lines.size)]
    }

    private val WEIGHTS = mapOf(
        PingKind.PENALTY to 5, PingKind.STREAK to 4, PingKind.TRIAL to 3, PingKind.PROGRESS to 3,
        PingKind.NUDGE to 3, PingKind.ARMORY to 2, PingKind.LORE to 2, PingKind.QUOTE to 2,
    )

    fun candidates(ctx: PingContext): List<Ping> = buildList {
        QUOTES.forEachIndexed { i, q -> add(Ping(PingKind.QUOTE, QUOTE_TITLES[i % QUOTE_TITLES.size], q, "q$i")) }
        LORE.forEachIndexed { i, (t, x) -> add(Ping(PingKind.LORE, t, x, "l$i")) }
        nudges(ctx).forEach { add(it) }
        progress(ctx).forEach { add(it) }
        streak(ctx).forEach { add(it) }
        for ((title, span, left) in ctx.trials) {
            val when_ = if (left <= 1) "Today is its last day." else "$left days remain."
            add(Ping(PingKind.TRIAL, "[TRIAL] $title", "$span objective in progress. $when_ Trials only reward — failing costs nothing.", "t:$title:$left"))
        }
        armory(ctx)?.let { add(it) }
        if (ctx.penalty.isNotEmpty()) {
            add(
                Ping(
                    PingKind.PENALTY, "[SYSTEM] Penalty Quest in effect",
                    "Today corrects yesterday: " + ctx.penalty.joinToString("; ") + ". Clear it to wipe the slate.",
                    "penalty",
                ),
            )
        }
    }

    private fun nudges(ctx: PingContext): List<Ping> = buildList {
        val h = ctx.hour
        fun n(key: String, title: String, text: String) = add(Ping(PingKind.NUDGE, title, text, "n:$key"))
        n("water", "[SYSTEM] Recovery check", "Drink a full glass of water. Hunters run on more than willpower.")
        n("posture", "[SYSTEM] Posture check", "Shoulders back, chin up, breathe out slowly. A hunter stands tall.")
        n("phone", "[SYSTEM] Focus window", "Put the phone face down for the next 30 minutes. AppBlock will thank you tonight.")
        if (ctx.walks && !ctx.vacation) {
            n("walk", "[SYSTEM] Movement opportunity", "Stand up and walk for five minutes. Steps taken now are steps you won't chase tonight.")
            n("stairs", "[SYSTEM] Side quest available", "Take the stairs or the long way round on your next errand. Small detours, big totals.")
        }
        if (ctx.wearsBands) {
            n("bands", "[SYSTEM] Iron Jaw reminder", "Rubber bands in? Every hour you wear them counts toward the goal — and extra hours earn bonus EXP.")
            if (h in 12..15) n("bands_meal", "[SYSTEM] After the meal", "Finished eating? Brush, then put the rubber bands straight back in.")
        }
        if (h < 13) {
            n("protein_am", "[SYSTEM] Fuel the hunt", "A protein-first meal today makes the evening calorie count much easier.")
        } else if (h < 17) {
            n("snack", "[SYSTEM] Craving detected", "Hungry between meals? Water first, wait ten minutes, then decide.")
            n("protein_pm", "[SYSTEM] Protein check", "Plan tonight's dinner around protein. The goal is easier with a plan than with luck.")
        } else {
            n("dinner", "[SYSTEM] Evening approach", "Decide dinner now, before hunger decides for you. Log it in Yazio as you eat.")
            if (ctx.skincare) n("skin", "[SYSTEM] Tonight's ritual", "Skincare before bed — two minutes. Set the bottles out now so you can't forget.")
        }
    }

    private fun progress(ctx: PingContext): List<Ping> = buildList {
        val level = ctx.level
        if (level < Rules.MAX_LEVEL) {
            val toNext = Rules.xpForLevel(level + 1) - ctx.xp
            add(Ping(PingKind.PROGRESS, "[STATUS] Level ${level + 1} in sight", "$toNext EXP separate you from level ${level + 1}. One clean day closes the gap fast.", "p:next"))
        }
        val nextRank = Rank.entries.firstOrNull { it.minLevel > level }
        if (nextRank != null) {
            val gap = nextRank.minLevel - level
            add(Ping(PingKind.PROGRESS, "[STATUS] Rank ${nextRank.label} awaits", "$gap level${if (gap == 1) "" else "s"} until you become ${nextRank.title}.", "p:rank"))
        }
        val nextSafe = (level / Rules.SAFE_ZONE_STEP + 1) * Rules.SAFE_ZONE_STEP
        if (nextSafe <= Rules.MAX_LEVEL) {
            add(Ping(PingKind.PROGRESS, "[STATUS] Next safe zone", "Reach level $nextSafe and it becomes a floor you can never fall below. A Random Box waits there too.", "p:safe"))
        }
    }

    private fun streak(ctx: PingContext): List<Ping> = buildList {
        if (ctx.loginAtRisk && !ctx.activeToday) {
            add(Ping(PingKind.STREAK, "[SYSTEM] Your flame is flickering", "${ctx.loginStreak}-day login streak. Log anything in Arise today to keep it burning.", "s:risk"))
        } else if (ctx.loginStreak >= 2) {
            add(Ping(PingKind.STREAK, "[SYSTEM] ${ctx.loginStreak}-day flame", "You've shown up ${ctx.loginStreak} days in a row. The System notices.", "s:login"))
        }
        if (ctx.questStreak >= 2) {
            add(Ping(PingKind.STREAK, "[SYSTEM] ${ctx.questStreak}-day quest streak", "${ctx.questStreak} Daily Quests cleared in a row. Tonight decides whether it becomes ${ctx.questStreak + 1}.", "s:quest"))
        }
    }

    private fun armory(ctx: PingContext): Ping? {
        val affordable = ctx.armory.filter { it.second <= ctx.gold }
        if (affordable.isNotEmpty()) {
            val (name, price) = affordable.last()
            return Ping(PingKind.ARMORY, "[ARMORY] Within reach", "You hold ${fmt(ctx.gold)} gold — enough for $name ($price G). Gear bought today can be equipped for tomorrow.", "a:$name")
        }
        val (name, price) = ctx.armory.firstOrNull() ?: return null
        return Ping(PingKind.ARMORY, "[ARMORY] Saving up", "${fmt(price - ctx.gold)} more gold for $name. Every cleared day pays.", "a:save:$name")
    }

    private fun fmt(n: Long): String = String.format(Locale.US, "%,d", n)
    private fun fmt(n: Int): String = fmt(n.toLong())

    private val QUOTE_TITLES = listOf(
        "[SYSTEM] Words for the hunt",
        "[SYSTEM] A message from the System",
        "[SYSTEM] Wisdom unlocked",
        "[SYSTEM] Mental fortitude +1",
        "[SYSTEM] Inscription found",
    )

    val QUOTES = listOf(
        "We are what we repeatedly do. Excellence, then, is not an act, but a habit.",
        "Discipline is choosing between what you want now and what you want most.",
        "The pain of discipline weighs ounces. The pain of regret weighs tons.",
        "No man is free who is not master of himself. — Epictetus",
        "You have power over your mind, not outside events. Realize this, and you will find strength. — Marcus Aurelius",
        "Waste no more time arguing what a good man should be. Be one. — Marcus Aurelius",
        "Difficulties strengthen the mind, as labor does the body. — Seneca",
        "It is not that we have a short time to live, but that we waste a lot of it. — Seneca",
        "First say to yourself what you would be; then do what you have to do. — Epictetus",
        "The strong do not wait for motivation. They act, and motivation follows.",
        "Every level is earned. Nothing is given.",
        "Your future self is watching. Make them proud.",
        "A hunter does not rest because the dungeon is hard.",
        "Small steps, every day. That is how the weakest become the strongest.",
        "You were not chosen because you were strong. You become strong because you keep going.",
        "Motivation fades. Systems remain.",
        "The only rank that matters is the one you earn today.",
        "Comfort is the enemy of growth.",
        "Do it tired. Do it bored. Do it anyway.",
        "Every craving you refuse makes the next one weaker.",
        "Hunger passes. Pride stays.",
        "Rise. Again. And again.",
        "Well done is better than well said. — Benjamin Franklin",
        "He who conquers himself is the mightiest warrior. — Confucius",
        "It does not matter how slowly you go as long as you do not stop. — Confucius",
        "The best time to start was yesterday. The next best time is now.",
        "Amateurs sit and wait for inspiration; the rest of us just get up and go to work. — Stephen King",
        "We suffer more often in imagination than in reality. — Seneca",
        "Knowing is not enough; we must apply. — Goethe",
        "The successful warrior is the average man, with laser-like focus. — Bruce Lee",
        "Absorb what is useful, discard what is useless, add what is specifically your own. — Bruce Lee",
        "A river cuts through rock not because of its power, but because of its persistence.",
        "You don't rise to the level of your goals. You fall to the level of your systems. — James Clear",
        "Every action you take is a vote for the type of person you wish to become. — James Clear",
        "The man who moves a mountain begins by carrying away small stones. — Confucius",
        "Fall seven times, stand up eight. — Japanese proverb",
        "What stands in the way becomes the way. — Marcus Aurelius",
        "Be tolerant with others and strict with yourself. — Marcus Aurelius",
        "Luck is what happens when preparation meets opportunity. — Seneca",
        "He who has a why to live can bear almost any how. — Nietzsche",
        "The body achieves what the mind believes.",
        "Strength does not come from winning. Your struggles develop your strengths.",
        "A year from now you will wish you had started today.",
        "Easy choices, hard life. Hard choices, easy life.",
        "Nobody is coming to save you. Get up.",
        "Weakness is a choice made one small surrender at a time. So is strength.",
        "The dungeon does not care how you feel. Neither does the scale. Show up.",
        "Win the morning, win the day.",
        "One more rep. One more page. One more hour without the phone.",
        "Discipline is remembering what you want.",
    )

    private val LORE = listOf(
        "[ALERT] Gate detected" to "A C-rank gate has opened somewhere in the city. Only hunters who kept their discipline will be ready when it breaks.",
        "[ALERT] Mana surge" to "Ambient mana is unusually high today. Rumour says effort spent now counts double in spirit — if not in EXP.",
        "[SYSTEM] Hidden quest whispers" to "Somewhere in the System lies a quest no one has seen. It reveals itself only to those who never skip a report.",
        "[ARCHIVE] Hunter's log" to "Day after day, the weakest hunter came back. No one remembers the day he got strong. Everyone remembers that he never stopped.",
        "[SYSTEM] Shadow stirs" to "Your shadow trains while you rest — but only if you gave it something to copy today.",
        "[ALERT] Red gate warning" to "Red gates trap the unprepared. The prepared walk out with loot. Which one are you building today?",
        "[ARCHIVE] Monarch's decree" to "A monarch is not crowned once. They are crowned every day they choose the harder path.",
        "[SYSTEM] Dungeon break forecast" to "Temptations peak in the late afternoon. Hunters who plan their evening meal survive the break.",
        "[ALERT] Guild recruitment" to "Top guilds scout hunters by consistency, not talent. Your record is your résumé.",
        "[ARCHIVE] Ancient rune" to "An inscription reads: ‘The chain is forged one link a day. Break one and you begin again.’",
        "[SYSTEM] Stat point pending" to "The System is weighing your stats. Movement feeds STR, nutrition VIT, focus PER. Feed them well tonight.",
        "[ALERT] Instance dungeon open" to "A personal dungeon has opened: the next hour, phone in another room. Enter at will.",
    )
}
