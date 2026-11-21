// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.data

import app.vzato.core.DoseLogger
import app.vzato.core.DoseNotifying
import app.vzato.core.DoseScheduler
import app.vzato.core.DoseStore
import app.vzato.core.DoseUnit
import app.vzato.core.MealRelation
import app.vzato.core.Medication
import app.vzato.core.Schedule
import app.vzato.core.StockService
import app.vzato.core.at
import app.vzato.core.day
import app.vzato.core.minutesOfDay
import java.time.Instant
import java.time.ZoneId

/**
 * Demo medications around the current time, as on iOS (`--sample-data`), for debug builds and
 * screenshots: `adb shell am start -n app.vzato/.MainActivity --ez sample-data true`.
 * Replaces nothing: it only runs on an empty database.
 */
object SampleData {
    fun populate(graph: Graph, store: DoseStore, now: Instant, zone: ZoneId) {
        if (graph.medications.isNotEmpty()) return
        val today = now.day(zone)
        val minutesNow = now.minutesOfDay(zone)
        fun clamp(minutes: Int) = minutes.coerceIn(0, 23 * 60 + 59)
        val start = today.minusDays(14).at(0, zone)
        fun make(name: String, dosage: String, hex: String, times: List<Int>, meal: MealRelation = MealRelation.ANY, units: Double = 1.0): Medication {
            val medication = Medication(
                name = name, dosageText = dosage, colorHex = hex, unitsPerDose = units, unit = DoseUnit.TABLET,
                createdAt = start, profileId = graph.defaultProfile.id, updatedAt = now,
            )
            medication.schedules += Schedule(minutesOfDay = times.map(::clamp), mealRelation = meal, startDate = start, updatedAt = now)
            graph.medications += medication
            return medication
        }
        val letrox = make("Letrox", "50 µg", "3B7DD8", listOf(minutesNow - 150, minutesNow + 300), MealRelation.BEFORE, 0.5)
        val ibuprofen = make("Ibuprofen", "400 mg", "D9534F", listOf(minutesNow - 25, minutesNow + 180), MealRelation.AFTER)
        val vitamin = make("Vitamin D", "", "E0A526", listOf(minutesNow + 90))
        StockService.setStock(ibuprofen, 4.0, start, store)
        ibuprofen.lowStockThreshold = 5.0
        StockService.setStock(letrox, 30.0, start, store)

        val quiet = object : DoseNotifying { override fun notifyLowStock(medication: Medication) {} }
        // The past two weeks: nearly everything taken, a few doses missed.
        for (back in 1..14) {
            val day = today.minusDays(back.toLong())
            for (medication in listOf(letrox, ibuprofen, vitamin)) {
                for (slot in DoseScheduler.slots(listOf(medication), day, zone)) {
                    if ((back + slot.scheduledAt.minutesOfDay(zone)) % 9 == 0) continue
                    DoseLogger(store, slot.scheduledAt.plusSeconds(240), quiet, zone).markTaken(slot)
                }
            }
        }
        // This morning's Letrox, a few minutes late.
        DoseScheduler.slots(listOf(letrox), today, zone).firstOrNull()?.let {
            DoseLogger(store, it.scheduledAt.plusSeconds(360), quiet, zone).markTaken(it)
        }
        // Supplies as counted now, after the history above.
        StockService.setStock(ibuprofen, 4.0, now, store, log = false)
        StockService.setStock(letrox, 21.5, now, store, log = false)
    }
}
