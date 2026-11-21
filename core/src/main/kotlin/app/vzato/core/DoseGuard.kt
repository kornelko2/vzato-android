// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.time.Duration
import java.time.Instant

/** Protection against a double dose: what one tap on "Taken" may record. */
object DoseGuard {
    /** A dose may be confirmed with one tap from this long before its time (and any time after, while open). */
    val earlyWindow: Duration = Duration.ofMinutes(30)

    sealed interface Verdict {
        data object Ok : Verdict

        /** The same medication was taken at this time, too recently for another dose. */
        data class RecentlyTaken(val at: Instant) : Verdict

        /** The dose is planned for this time, more than [earlyWindow] from now. */
        data class TooEarly(val at: Instant) : Verdict
    }

    /** Recently taken wins over too early: a second dose is the bigger danger. */
    fun verdict(slot: DoseSlot, now: Instant): Verdict {
        lastTaken(slot.medication, now, slot.event)?.let { last ->
            if (Duration.between(last, now) < minimumGap(slot.medication)) return Verdict.RecentlyTaken(last)
        }
        if (Duration.between(now, slot.scheduledAt) > earlyWindow) return Verdict.TooEarly(slot.scheduledAt)
        return Verdict.Ok
    }

    /** For an extra dose outside the plan only the "recently taken" part applies. */
    fun verdictForExtraDose(medication: Medication, at: Instant): Verdict {
        lastTaken(medication, at, null)?.let { last ->
            if (Duration.between(last, at) < minimumGap(medication)) return Verdict.RecentlyTaken(last)
        }
        return Verdict.Ok
    }

    /**
     * Half of the shortest gap between the planned times, between 1 and 3 hours. A medication taken
     * once a day, or with no plan, gets 3 hours.
     */
    fun minimumGap(medication: Medication): Duration {
        val minutes = medication.activeSchedules.flatMap { it.minutesOfDay }.toSortedSet().toList()
        if (minutes.size <= 1) return Duration.ofHours(3)
        val gaps = minutes.zipWithNext { a, b -> b - a } + (minutes.first() + 24 * 60 - minutes.last())
        val halfSeconds = (gaps.min() * 60) / 2L
        return Duration.ofSeconds(halfSeconds.coerceIn(3600L, 3 * 3600L))
    }

    fun lastTaken(medication: Medication, before: Instant, excluding: DoseEvent?): Instant? =
        medication.events
            .filter { it.status == DoseStatus.TAKEN && it !== excluding }
            .mapNotNull { it.takenAt }
            .filter { it <= before }
            .maxOrNull()
}
