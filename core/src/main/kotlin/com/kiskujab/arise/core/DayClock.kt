package com.kiskujab.arise.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Time rules. A day's entries lock at midnight; phone usage (screen time, distraction) can still be
 * reported until noon the next day. A day is finalized at that noon, or earlier if the player seals it.
 */
object DayClock {

    fun phoneDeadline(day: LocalDate): LocalDateTime =
        day.plusDays(1).atTime(LocalTime.of(Rules.PHONE_REPORT_DEADLINE_HOUR, 0))

    fun canEditEntries(day: LocalDate, now: LocalDateTime): Boolean = day == now.toLocalDate()

    fun canEditPhone(day: LocalDate, now: LocalDateTime): Boolean =
        day == now.toLocalDate() || (day == now.toLocalDate().minusDays(1) && now.isBefore(phoneDeadline(day)))

    /** Can be sealed early by the player: entries are locked, only the phone window is still open. */
    fun canSeal(day: LocalDate, now: LocalDateTime): Boolean = day.isBefore(now.toLocalDate())

    /** Must be finalized now: the phone window has closed. */
    fun mustFinalize(day: LocalDate, now: LocalDateTime): Boolean = !now.isBefore(phoneDeadline(day))

    /** Days in (lastFinalized, now] that must be finalized, oldest first. */
    fun daysToFinalize(lastFinalized: LocalDate, now: LocalDateTime): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = lastFinalized.plusDays(1)
        while (mustFinalize(d, now)) {
            out += d
            d = d.plusDays(1)
        }
        return out
    }
}
