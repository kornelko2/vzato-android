// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** One dose as the Today card and the widget see it at one moment. */
data class TodayDose(
    val slot: DoseSlot,
    val status: DoseStatus,
    val isOverdue: Boolean,
    /** No record yet: can still be confirmed. */
    val isOpen: Boolean,
    /** One tap on "Taken" is safe (DoseGuard). Otherwise only the time shows. */
    val canTakeNow: Boolean,
) {
    val id: String get() = slot.id
    val scheduledAt: Instant get() = slot.scheduledAt

    /** Due now, overdue or missed: offered with a Taken button. */
    val isCurrent: Boolean get() = isOpen && (isOverdue || status == DoseStatus.MISSED)
}

data class TodayEntry(
    val date: Instant,
    /** The big card: oldest overdue, else oldest missed, else the next one. */
    val featured: TodayDose?,
    /** Other open doses still to come or overdue, current ones first. */
    val upcoming: List<TodayDose>,
    val taken: Int,
    val total: Int,
)

object TodaySnapshot {
    /**
     * One entry now and one at every moment the picture changes today: each open dose becomes
     * overdue at its time and missed after the grace period.
     */
    fun entries(medications: List<Medication>, now: Instant, grace: Duration, zone: ZoneId, limit: Int = 40): List<TodayEntry> {
        val slots = DoseScheduler.slots(medications, now.day(zone), zone)
        val moments = sortedSetOf(now)
        for (slot in slots) {
            if (slot.event != null) continue
            if (slot.scheduledAt > now) moments += slot.scheduledAt
            val expiry = slot.scheduledAt.plus(grace)
            if (expiry > now && expiry.day(zone) == now.day(zone)) moments += expiry
        }
        return moments.take(limit).map { entry(it, slots, grace) }
    }

    fun entry(at: Instant, slots: List<DoseSlot>, grace: Duration): TodayEntry {
        val doses = slots.map { dose(it, at, grace) }
        val open = doses.filter { it.isOpen }
        val featured = open.firstOrNull { it.isOverdue }
            ?: open.firstOrNull { it.status == DoseStatus.MISSED }
            ?: open.firstOrNull { it.scheduledAt > at }
        val rest = open.filter { it.id != featured?.id }
        val upcoming = rest.filter { it.isCurrent } + rest.filter { !it.isCurrent && it.status == DoseStatus.PENDING }
        return TodayEntry(at, featured, upcoming, doses.count { it.status == DoseStatus.TAKEN }, doses.size)
    }

    fun dose(slot: DoseSlot, at: Instant, grace: Duration) = TodayDose(
        slot = slot,
        status = slot.status(at, grace),
        isOverdue = slot.isOverdue(at, grace),
        isOpen = slot.event == null,
        canTakeNow = slot.event == null && DoseGuard.verdict(slot, at) == DoseGuard.Verdict.Ok,
    )
}

/** Counts for a day or a period. */
data class DaySummary(
    val total: Int = 0,
    val taken: Int = 0,
    val skipped: Int = 0,
    val missed: Int = 0,
    val pending: Int = 0,
) {
    /** Doses with a final state; pending ones do not count against adherence. */
    val decided: Int get() = taken + missed + skipped

    /** Share of decided doses that were taken, null until something was decided. */
    val adherence: Double? get() = if (decided > 0) taken.toDouble() / decided else null

    operator fun plus(other: DaySummary) = DaySummary(
        total + other.total, taken + other.taken, skipped + other.skipped, missed + other.missed, pending + other.pending,
    )
}

object DoseSummary {
    fun summarize(slots: List<DoseSlot>, now: Instant, grace: Duration): DaySummary =
        slots.fold(DaySummary()) { summary, slot ->
            when (slot.status(now, grace)) {
                DoseStatus.TAKEN -> summary.copy(total = summary.total + 1, taken = summary.taken + 1)
                DoseStatus.SKIPPED -> summary.copy(total = summary.total + 1, skipped = summary.skipped + 1)
                DoseStatus.MISSED -> summary.copy(total = summary.total + 1, missed = summary.missed + 1)
                DoseStatus.PENDING -> summary.copy(total = summary.total + 1, pending = summary.pending + 1)
            }
        }

    /** One summary per day of the month that has at least one dose. */
    fun byDay(
        medications: List<Medication>,
        month: java.time.YearMonth,
        now: Instant,
        grace: Duration,
        zone: ZoneId,
    ): Map<java.time.LocalDate, DaySummary> =
        (1..month.lengthOfMonth()).mapNotNull { dayOfMonth ->
            val day = month.atDay(dayOfMonth)
            val summary = summarize(DoseScheduler.slots(medications, day, zone), now, grace)
            if (summary.total > 0) day to summary else null
        }.toMap()

    fun total(summaries: Collection<DaySummary>): DaySummary = summaries.fold(DaySummary()) { a, b -> a + b }
}
