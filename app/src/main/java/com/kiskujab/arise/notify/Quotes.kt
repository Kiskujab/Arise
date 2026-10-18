package com.kiskujab.arise.notify

import kotlin.random.Random

object Quotes {
    private val lines = listOf(
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
        "The weak blame the circumstances. The strong change them.",
        "Your future self is watching. Make them proud.",
        "One more rep. One more page. One more hour without the phone.",
        "A hunter does not rest because the dungeon is hard.",
        "Small steps, every day. That is how the weakest become the strongest.",
        "You were not chosen because you were strong. You become strong because you keep going.",
        "Motivation fades. Systems remain.",
        "The only rank that matters is the one you earn today.",
        "Comfort is the enemy of growth.",
        "Do it tired. Do it bored. Do it anyway.",
        "Every craving you refuse makes the next one weaker.",
        "Put the phone down. Your quests are waiting.",
        "Hunger passes. Pride stays.",
        "The System remembers everything. So should you.",
        "Rise. Again. And again.",
        "Well done is better than well said. — Benjamin Franklin",
        "He who conquers himself is the mightiest warrior. — Confucius",
        "It does not matter how slowly you go as long as you do not stop. — Confucius",
        "The best time to start was yesterday. The next best time is now.",
    )

    fun random(random: Random = Random.Default): String = lines[random.nextInt(lines.size)]
}
