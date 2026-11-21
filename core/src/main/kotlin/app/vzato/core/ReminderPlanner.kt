// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** One planned notification: every medication due at one time, or a follow-up of it. */
data class PlannedReminder(
    /** The dose's day as "yyyy-MM-dd". */
    val dayKey: String,
    val minutesOfDay: Int,
    val attempt: Int,
    val fireAt: Instant,
    /** One entry per dose. */
    val medicationIds: List<UUID>,
    /** Each dose's own time, parallel to [medicationIds]. Empty means all at [minutesOfDay]. */
    val doseMinutes: List<Int> = emptyList(),
    /** Set when the reminder stands for an organizer compartment ("Morning: …"). */
    val compartment: String? = null,
) {
    val resolvedDoseMinutes: List<Int>
        get() = if (doseMinutes.size == medicationIds.size) doseMinutes else medicationIds.map { minutesOfDay }

    /** The same identifiers as iOS: dose|<yyyy-MM-dd>|<minutes>|<attempt>. */
    val identifier: String get() = "dose|$dayKey|$minutesOfDay|$attempt"
    val groupKey: String get() = "$dayKey|$minutesOfDay"
}

data class ReminderPlan(
    val reminders: List<PlannedReminder> = emptyList(),
    /** Time of the last dose that got its reminders; null when nothing was planned. */
    val plannedUntil: Instant? = null,
    /** True when the budget ran out before the horizon: the app has to plan again later. */
    val limited: Boolean = false,
)

/**
 * One notification per time with every medication due then, planned per concrete dose (no
 * repeating triggers), with follow-ups until the dose counts as missed.
 */
object ReminderPlanner {
    /** How far ahead doses are planned at most. */
    const val HORIZON_DAYS = 14

    data class Settings(val snoozeMinutes: Int, val repeatCount: Int, val missedAfterMinutes: Int)

    /** Organizer compartment of a dose: a key unique per profile and compartment, and its name. */
    data class OrganizerSlot(val key: String, val compartment: String)

    /**
     * `slots` are open scheduled doses (no record yet), any order. Doses whose reminders are all in
     * the past are skipped. Doses at the same minute share one notification. Groups are planned in
     * time order, each with all its follow-ups or not at all, until `budget` is used up.
     * `snoozed` maps a dose time ("yyyy-MM-dd|minutes") to the medications that have their own
     * snoozed reminder for it. Organizer doses share one reminder at the earliest open dose of
     * their compartment.
     */
    fun plan(
        slots: List<DoseSlot>,
        now: Instant,
        settings: Settings,
        budget: Int,
        zone: ZoneId,
        snoozed: Map<String, Set<UUID>> = emptyMap(),
        organizer: (DoseSlot) -> OrganizerSlot? = { null },
    ): ReminderPlan {
        fun minutes(at: Instant) = at.minutesOfDay(zone)
        val open = slots.filter { slot ->
            snoozed["${dayKey(slot.scheduledAt, zone)}|${minutes(slot.scheduledAt)}"]?.contains(slot.medication.id) != true
        }
        val compartmentStart = mutableMapOf<String, Instant>()
        fun compartmentKey(slot: DoseSlot) = organizer(slot)?.let { "${dayKey(slot.scheduledAt, zone)}|${it.key}" }
        for (slot in open) {
            val key = compartmentKey(slot) ?: continue
            compartmentStart[key] = minOf(compartmentStart[key] ?: slot.scheduledAt, slot.scheduledAt)
        }
        fun reminderTime(slot: DoseSlot) = compartmentKey(slot)?.let { compartmentStart[it] } ?: slot.scheduledAt
        val compartments = mutableMapOf<Long, String>()
        for (slot in open) organizer(slot)?.let { compartments[reminderTime(slot).epochMinute] = it.compartment }

        val reminders = mutableListOf<PlannedReminder>()
        var plannedUntil: Instant? = null
        var limited = false
        val groups = open.groupBy { reminderTime(it).epochMinute }
        for (key in groups.keys.sorted()) {
            val members = groups.getValue(key)
            val scheduledAt = reminderTime(members.first())
            val day = dayKey(scheduledAt, zone)
            val groupMinutes = minutes(scheduledAt)
            val sorted = members.sortedWith(compareBy<DoseSlot> { it.scheduledAt }.thenBy { it.medication.name.lowercase(Locale.ROOT) })
            val ids = sorted.map { it.medication.id }
            val doseMinutes = sorted.map { minutes(it.scheduledAt) }
            val missedAt = scheduledAt.plusMinutes(settings.missedAfterMinutes.toLong())
            val sequence = (0..maxOf(0, settings.repeatCount)).mapNotNull { attempt ->
                val fireAt = scheduledAt.plusMinutes(attempt.toLong() * settings.snoozeMinutes)
                if (fireAt <= now || (attempt > 0 && fireAt > missedAt)) {
                    null
                } else {
                    PlannedReminder(day, groupMinutes, attempt, fireAt, ids, doseMinutes, compartments[key])
                }
            }
            if (sequence.isEmpty()) continue
            if (reminders.size + sequence.size > budget) {
                limited = true
                break
            }
            reminders += sequence
            plannedUntil = scheduledAt
        }
        return ReminderPlan(reminders, plannedUntil, limited)
    }

    fun dayKey(at: Instant, zone: ZoneId): String = at.day(zone).toString()

    fun day(fromKey: String): LocalDate? = runCatching { LocalDate.parse(fromKey) }.getOrNull()
}
