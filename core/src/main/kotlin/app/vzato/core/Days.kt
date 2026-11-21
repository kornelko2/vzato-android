// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// Calendar arithmetic in one place, always through java.time: never seconds math, which breaks on
// the days the clocks change. Days are LocalDate in the given zone, moments are Instant.

// atZone, not LocalDate.ofInstant: that one needs Android 14 (API 34).
fun Instant.day(zone: ZoneId): LocalDate = atZone(zone).toLocalDate()

fun LocalDate.start(zone: ZoneId): Instant = atStartOfDay(zone).toInstant()

/** The moment `minutes` after midnight on this day. On a spring-forward gap it moves forward. */
fun LocalDate.at(minutes: Int, zone: ZoneId): Instant =
    atTime(LocalTime.of(minutes / 60, minutes % 60)).atZone(zone).toInstant()

fun Instant.minutesOfDay(zone: ZoneId): Int = atZone(zone).let { it.hour * 60 + it.minute }

/** Whole minutes since the epoch: two times in the same minute are the same dose time. */
val Instant.epochMinute: Long get() = Math.floorDiv(epochSecond, 60L)

fun Instant.sameMinute(other: Instant): Boolean = epochMinute == other.epochMinute

/** iOS Calendar weekday numbering, used in stored data: 1 = Sunday … 7 = Saturday. */
val LocalDate.appleWeekday: Int get() = dayOfWeek.appleWeekday

val DayOfWeek.appleWeekday: Int get() = value % 7 + 1

fun Instant.plusMinutes(minutes: Long): Instant = plusSeconds(minutes * 60)
