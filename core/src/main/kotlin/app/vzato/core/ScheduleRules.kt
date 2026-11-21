// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Repeat rules of a plan, the same as Schedule.swift on iOS. */
object ScheduleRules {
    /** Typical dose times: morning, noon, evening, night. */
    val suggestedMinutes = listOf(8 * 60, 12 * 60, 18 * 60, 22 * 60)

    /**
     * Whether the plan has doses on this day by its repeat rule only (start, end and pause are
     * checked by DoseScheduler). Intervals count from the start date; "every month" keeps the
     * start's day of the month and falls back to the month's last day (31 Jan → 28 Feb → 31 Mar).
     */
    fun occurs(
        kind: RepeatKind,
        interval: Int,
        weekdays: List<Int>,
        start: Instant?,
        day: LocalDate,
        zone: ZoneId,
    ): Boolean {
        val n = maxOf(1, interval)
        return when (kind) {
            RepeatKind.WEEKDAYS -> day.appleWeekday in weekdays
            RepeatKind.DAYS, RepeatKind.WEEKS -> {
                if (start == null) return n == 1 && kind == RepeatKind.DAYS
                val step = if (kind == RepeatKind.DAYS) n else n * 7
                val offset = ChronoUnit.DAYS.between(start.day(zone), day)
                offset >= 0 && offset % step == 0L
            }
            RepeatKind.MONTHS -> {
                if (start == null) return false
                val anchor = start.day(zone)
                val months = (day.year - anchor.year) * 12 + (day.monthValue - anchor.monthValue)
                months >= 0 && months % n == 0 && anchor.plusMonths(months.toLong()) == day
            }
        }
    }

    fun occurs(schedule: Schedule, day: LocalDate, zone: ZoneId): Boolean =
        occurs(schedule.repeatKind, schedule.repeatInterval, schedule.weekdays, schedule.startDate, day, zone)

    /** The next days with doses, from `from` on (inclusive), at most `limit`, looking `horizonDays` ahead. */
    fun occurrenceDays(
        kind: RepeatKind,
        interval: Int,
        weekdays: List<Int>,
        start: Instant?,
        end: Instant?,
        from: LocalDate,
        limit: Int,
        zone: ZoneId,
        horizonDays: Int = 400,
    ): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var current = from
        val first = start?.day(zone)
        if (first != null && current < first) current = first
        val last = end?.day(zone)
        repeat(maxOf(0, horizonDays)) {
            if (result.size >= limit) return result
            if (last != null && current > last) return result
            if (occurs(kind, interval, weekdays, start, current, zone)) result += current
            current = current.plusDays(1)
        }
        return result
    }

    fun occurrenceDays(schedule: Schedule, from: LocalDate, limit: Int, zone: ZoneId, horizonDays: Int = 400) =
        occurrenceDays(
            schedule.repeatKind, schedule.repeatInterval, schedule.weekdays, schedule.startDate, schedule.endDate,
            from, limit, zone, horizonDays,
        )

    /** "Add time" offers the next typical time not used yet, then hourly after the last one, never a duplicate. */
    fun nextSuggestedMinutes(existing: List<Int>): Int {
        val taken = existing.toSet()
        suggestedMinutes.firstOrNull { it !in taken }?.let { return it }
        var candidate = (existing.maxOrNull() ?: (8 * 60)) + 60
        while ((candidate % (24 * 60)) in taken) candidate += 60
        return candidate % (24 * 60)
    }

    /** "1,2,3" as stored, the same format as iOS. */
    fun parse(raw: String): List<Int> = raw.split(",").mapNotNull { it.trim().toIntOrNull() }

    fun encode(values: List<Int>): String = values.toSortedSet().joinToString(",")

    fun weekdays(raw: String): List<Int> = parse(raw).filter { it in 1..7 }

    fun minutes(raw: String): List<Int> = parse(raw).filter { it in 0 until 24 * 60 }
}
