// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One dose as the screens show it: planned (maybe without a record yet) or recorded outside the plan. */
data class DoseSlot(
    val medication: Medication,
    val scheduledAt: Instant,
    val event: DoseEvent?,
) {
    val id: String
        get() = "${medication.id}|${scheduledAt.epochSecond}|${if (event?.isUnscheduled == true) "x" else "s"}"

    val isUnscheduled: Boolean get() = event?.isUnscheduled ?: false

    fun status(now: Instant, grace: Duration): DoseStatus {
        event?.let { return it.status }
        return if (scheduledAt.plus(grace) <= now) DoseStatus.MISSED else DoseStatus.PENDING
    }

    fun isOverdue(now: Instant, grace: Duration): Boolean =
        status(now, grace) == DoseStatus.PENDING && scheduledAt <= now

    /** Due now, overdue, or missed without a record: every place that shows the dose offers "Taken". */
    fun isCurrent(now: Instant): Boolean = event == null && scheduledAt <= now
}

/**
 * Expected doses per day, merged with the recorded events. A dose has no record until the user
 * acts; its status is derived (pending, then missed after the grace period).
 */
object DoseScheduler {
    /** The schedule that produced a dose at this time, if any. */
    fun schedule(of: Medication, at: Instant, zone: ZoneId): Schedule? =
        of.activeSchedules.firstOrNull { schedule ->
            times(schedule, at.day(zone), zone).any { it.sameMinute(at) }
        }

    /** When the person may eat after a "before a meal" dose, or null. */
    fun mealReadyTime(event: DoseEvent, zone: ZoneId): Instant? {
        if (event.status != DoseStatus.TAKEN || event.isUnscheduled) return null
        val medication = event.medication ?: return null
        val scheduledAt = event.scheduledAt ?: return null
        val takenAt = event.takenAt ?: return null
        val schedule = schedule(medication, scheduledAt, zone) ?: return null
        if (schedule.mealRelation != MealRelation.BEFORE) return null
        return takenAt.plusMinutes(maxOf(1, schedule.mealWaitMinutes).toLong())
    }

    /** Scheduled times of one schedule on one day, or empty if the schedule does not apply. */
    fun times(schedule: Schedule, day: LocalDate, zone: ZoneId): List<Instant> {
        if (schedule.isPaused) return emptyList()
        schedule.startDate?.let { if (day < it.day(zone)) return emptyList() }
        schedule.endDate?.let { if (day > it.day(zone)) return emptyList() }
        if (!ScheduleRules.occurs(schedule, day, zone)) return emptyList()
        return schedule.minutesOfDay.sorted().map { day.at(it, zone) }
    }

    /**
     * All slots of the given medications on one day, scheduled ones merged with existing events,
     * plus extra doses logged that day. Sorted by time.
     */
    fun slots(medications: List<Medication>, day: LocalDate, zone: ZoneId): List<DoseSlot> {
        val result = mutableListOf<DoseSlot>()
        for (medication in medications) {
            val dayEvents = medication.events.filter { it.scheduledAt?.day(zone) == day }
            val generatesDoses = medication.archivedAt?.let { day <= it.day(zone) } ?: true
            val used = mutableSetOf<DoseEvent>()
            if (generatesDoses) {
                val seenTimes = mutableSetOf<Long>()
                for (schedule in medication.activeSchedules) {
                    for (time in times(schedule, day, zone)) {
                        if (!seenTimes.add(time.epochMinute)) continue
                        // Two devices may have recorded this dose before syncing: show the
                        // preferred record and swallow the rest.
                        val matches = dayEvents.filter { event ->
                            !event.isUnscheduled && event !in used && event.scheduledAt?.sameMinute(time) == true
                        }
                        val match = DoseEvent.preferred(matches)
                        used += matches
                        // A dose from before the medication was added was never asked for: not missed.
                        val created = medication.createdAt
                        if (match == null && created != null && time < created) continue
                        result += DoseSlot(medication, time, match)
                    }
                }
            }
            for (event in dayEvents) {
                if (event.isUnscheduled) event.scheduledAt?.let { result += DoseSlot(medication, it, event) }
            }
            // Recorded doses whose plan time no longer exists (plan changed): keep them in history.
            for (event in dayEvents) {
                if (event.isUnscheduled || event in used || event.status == DoseStatus.PENDING) continue
                event.scheduledAt?.let { result += DoseSlot(medication, it, event) }
            }
        }
        return result.sortedBy { it.scheduledAt }
    }

    /** Slots across several days, limited to [from, to] inclusive. */
    fun slots(medications: List<Medication>, from: Instant, to: Instant, zone: ZoneId): List<DoseSlot> {
        val result = mutableListOf<DoseSlot>()
        var day = from.day(zone)
        val last = to.day(zone)
        while (day <= last) {
            result += slots(medications, day, zone).filter { it.scheduledAt >= from && it.scheduledAt <= to }
            day = day.plusDays(1)
        }
        return result
    }

    /** Next scheduled dose of a medication after a point in time, looking ahead a limited number of days. */
    fun nextSlot(medication: Medication, after: Instant, zone: ZoneId, lookaheadDays: Int = 14): DoseSlot? {
        var day = after.day(zone)
        repeat(lookaheadDays + 1) {
            slots(listOf(medication), day, zone)
                .firstOrNull { it.scheduledAt > after && !it.isUnscheduled }
                ?.let { return it }
            day = day.plusDays(1)
        }
        return null
    }

    /**
     * Taken doses that no longer belong to a time of the plan: the time was changed after the
     * dose, or the person travelled to another time zone. Looks at the slot's day and the day before.
     */
    fun orphanTakenTimes(medication: Medication, near: Instant, zone: ZoneId): List<Instant> {
        val day = near.day(zone)
        return listOf(day.minusDays(1), day).flatMap { d ->
            val planned = medication.activeSchedules.flatMap { times(it, d, zone) }.map { it.epochMinute }.toSet()
            medication.events.mapNotNull { event ->
                val at = event.scheduledAt ?: return@mapNotNull null
                if (event.status != DoseStatus.TAKEN || event.isUnscheduled) return@mapNotNull null
                if (at.day(zone) != d || at.epochMinute in planned) return@mapNotNull null
                event.takenAt ?: at
            }
        }
    }

    /**
     * An open dose that comes too soon after such a taken dose gets no reminder: it is most likely
     * the same dose at its new time. The Today card still shows it and asks before recording.
     */
    fun isCoveredByMovedDose(slot: DoseSlot, zone: ZoneId): Boolean {
        val gap = DoseGuard.minimumGap(slot.medication)
        return orphanTakenTimes(slot.medication, slot.scheduledAt, zone).any { taken ->
            taken <= slot.scheduledAt && Duration.between(taken, slot.scheduledAt) < gap
        }
    }
}
