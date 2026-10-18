package com.kiskujab.arise.core

/** Hunter ranks. S needs roughly a year of consistently good days; the ones above it take longer. */
enum class Rank(val label: String, val minLevel: Int, val color: Long) {
    E("E", 1, 0xFF8A94A6),
    D("D", 10, 0xFF4CD964),
    C("C", 20, 0xFF3DA9FC),
    B("B", 35, 0xFF7B61FF),
    A("A", 50, 0xFFFF9F1C),
    S("S", 70, 0xFFFF3B5C),
    S_PLUS("S+", 80, 0xFFFF2E93),
    MYTHICAL("Mythical", 90, 0xFFB388FF),
    GRANDMASTER("Grandmaster", 100, 0xFFFFD166),
    CHALLENGER("Challenger", 110, 0xFF7DF9FF);

    val title: String get() = if (ordinal <= S_PLUS.ordinal) "$label-Rank Hunter" else label

    companion object {
        fun forLevel(level: Int): Rank = entries.last { level >= it.minLevel }
    }
}
