// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// The same fixtures as VzatoTests/TestSupport.swift: Europe/Prague, Monday 2 November 2026.

val zone: ZoneId = ZoneId.of("Europe/Prague")
val monday: LocalDate = LocalDate.of(2026, 11, 2)

fun at(day: LocalDate, hour: Int, minute: Int = 0): Instant = day.at(hour * 60 + minute, zone)

fun days(count: Int, after: LocalDate): LocalDate = after.plusDays(count.toLong())

val twoHours: Duration = Duration.ofHours(2)

/** Keeps everything in memory and counts saves; the medication list stands for the database. */
class TestStore : DoseStore, ActivityTexts {
    val medications = mutableListOf<Medication>()
    val activity = mutableListOf<ActivityEntry>()
    val deletedEvents = mutableListOf<DoseEvent>()
    var saves = 0
    override val deviceName = "Test device"
    override val texts: ActivityTexts get() = this

    fun medication(
        name: String = "Ibuprofen",
        times: List<Int> = listOf(8 * 60, 20 * 60),
        weekdays: List<Int> = (1..7).toList(),
        start: LocalDate = monday,
        end: LocalDate? = null,
        stock: Double? = null,
        unitsPerDose: Double = 1.0,
        repeat: RepeatKind = RepeatKind.WEEKDAYS,
        every: Int = 1,
        profileId: UUID? = null,
    ): Medication {
        val medication = Medication(name = name, createdAt = start.start(zone), unitsPerDose = unitsPerDose, profileId = profileId)
        medication.schedules += Schedule(
            minutesOfDay = times,
            weekdays = weekdays,
            repeatKind = repeat,
            repeatIntervalCount = every,
            startDate = start.start(zone),
            endDate = end?.start(zone),
        )
        if (stock != null) StockService.setStock(medication, stock, start.start(zone), this)
        medications += medication
        return medication
    }

    fun logger(now: Instant, notifier: DoseNotifying = SpyNotifier()) = DoseLogger(this, now, notifier, zone)

    override fun inserted(event: DoseEvent) {}
    override fun deleted(event: DoseEvent) { deletedEvents += event }
    override fun log(entry: ActivityEntry) { activity += entry }
    override fun save() { saves++ }

    override fun recordedLater(takenAt: Instant) = "Recorded later, taken at $takenAt."
    override fun previously(status: DoseStatus) = "Previously ${status.raw}."
    override fun stockCounted(medication: Medication, count: Double, previous: Double?) =
        if (previous != null) "$count, was $previous." else "$count"
    override fun supplyCheck(medication: Medication, counted: Double, expected: Double, backfilled: Int) =
        "Counted $counted, records expected $expected." + if (backfilled > 0) " Recorded $backfilled missing doses as taken." else ""
}

class SpyNotifier : DoseNotifying {
    val lowStockNames = mutableListOf<String>()
    val mealReady = mutableListOf<Pair<String, Instant>>()
    val mealCancelled = mutableListOf<Instant>()
    val resolved = mutableListOf<Instant>()

    override fun notifyLowStock(medication: Medication) { lowStockNames += medication.name }
    override fun doseResolved(medicationId: UUID, scheduledAt: Instant) { resolved += scheduledAt }
    override fun scheduleMealReady(medicationId: UUID, name: String, scheduledAt: Instant, at: Instant, waitMinutes: Int) {
        mealReady += name to at
    }
    override fun cancelMealReady(medicationId: UUID, scheduledAt: Instant) { mealCancelled += scheduledAt }
}

fun slots(medication: Medication, day: LocalDate) = DoseScheduler.slots(listOf(medication), day, zone)
