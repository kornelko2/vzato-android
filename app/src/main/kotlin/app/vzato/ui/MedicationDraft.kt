// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui

import app.vzato.core.DoseLogger
import app.vzato.core.DoseStore
import app.vzato.core.DoseUnit
import app.vzato.core.MealRelation
import app.vzato.core.Medication
import app.vzato.core.RepeatKind
import app.vzato.core.Schedule
import app.vzato.core.StockService
import app.vzato.core.start
import app.vzato.data.Graph
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** What the medication form edits. One plan per medication, as in the iOS form. */
data class MedicationDraft(
    val id: UUID? = null,
    val name: String = "",
    val dosageText: String = "",
    val unit: DoseUnit = DoseUnit.TABLET,
    val unitsPerDose: Double = 1.0,
    val times: List<Int> = listOf(8 * 60),
    val repeatKind: RepeatKind = RepeatKind.WEEKDAYS,
    val weekdays: List<Int> = (1..7).toList(),
    val interval: Int = 1,
    val mealRelation: MealRelation = MealRelation.ANY,
    val mealWaitMinutes: Int = 30,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val colorHex: String = "",
    val notes: String = "",
    val tracksStock: Boolean = false,
    /** Typed supply; saved as a new count only when it differs from the current one. */
    val stockCount: Double? = null,
    val lowStockThreshold: Double = 0.0,
    val profileId: UUID? = null,
) {
    val isValid: Boolean get() = name.isNotBlank() && times.isNotEmpty() && (repeatKind != RepeatKind.WEEKDAYS || weekdays.isNotEmpty())

    /** Creates or updates the medication in the graph; returns its id. */
    fun save(graph: Graph, store: DoseStore, now: Instant, zone: ZoneId = ZoneId.systemDefault()): UUID {
        val medication = id?.let(graph::medication) ?: Medication(
            createdAt = now,
            profileId = profileId ?: graph.defaultProfile.id,
            colorHex = colorHex.ifBlank { nextColor(graph) },
        ).also { graph.medications += it }
        if (medication.unitsPerDose != unitsPerDose || medication.unit != unit) DoseLogger.keepAmounts(medication)
        medication.name = name.trim()
        medication.dosageText = dosageText.trim()
        medication.unit = unit
        medication.unitsPerDose = unitsPerDose
        medication.notes = notes.trim()
        if (colorHex.isNotBlank()) medication.colorHex = colorHex
        medication.lowStockThreshold = lowStockThreshold
        medication.updatedAt = now
        val schedule = medication.schedules.firstOrNull() ?: Schedule().also { medication.schedules += it }
        // Extra plans (from another device) stay as they are; the form edits the first one.
        schedule.minutesOfDay = times.distinct().sorted()
        schedule.repeatKind = repeatKind
        schedule.weekdays = if (repeatKind == RepeatKind.WEEKDAYS) weekdays.distinct().sorted() else (1..7).toList()
        schedule.repeatIntervalCount = maxOf(1, interval)
        schedule.mealRelation = mealRelation
        schedule.mealWaitMinutes = mealWaitMinutes
        schedule.startDate = startDate.start(zone)
        schedule.endDate = endDate?.start(zone)
        schedule.updatedAt = now
        if (!tracksStock) {
            medication.tracksStock = false
        } else if (stockCount != null && (!medication.tracksStock || stockCount != medication.stockCount)) {
            StockService.setStock(medication, stockCount, now, store)
        }
        return medication.id
    }

    companion object {
        fun from(medication: Medication, zone: ZoneId = ZoneId.systemDefault()): MedicationDraft {
            val schedule = medication.schedules.firstOrNull()
            return MedicationDraft(
                id = medication.id,
                name = medication.name,
                dosageText = medication.dosageText,
                unit = medication.unit,
                unitsPerDose = medication.unitsPerDose,
                times = schedule?.minutesOfDay ?: emptyList(),
                repeatKind = schedule?.repeatKind ?: RepeatKind.WEEKDAYS,
                weekdays = schedule?.weekdays ?: (1..7).toList(),
                interval = schedule?.repeatInterval ?: 1,
                mealRelation = schedule?.mealRelation ?: MealRelation.ANY,
                mealWaitMinutes = schedule?.mealWaitMinutes ?: 30,
                startDate = schedule?.startDate?.let { it.atZone(zone).toLocalDate() } ?: LocalDate.now(zone),
                endDate = schedule?.endDate?.let { it.atZone(zone).toLocalDate() },
                colorHex = medication.colorHex,
                notes = medication.notes,
                tracksStock = medication.tracksStock,
                stockCount = if (medication.tracksStock) medication.stockCount else null,
                lowStockThreshold = medication.lowStockThreshold,
                profileId = medication.profileId,
            )
        }

        /** The first medication color not used yet, so a new one stands out. */
        fun nextColor(graph: Graph): String {
            val used = graph.medications.map { it.colorHex }.toSet()
            return Palette.medicationHexes.firstOrNull { it !in used } ?: Palette.medicationHexes[graph.medications.size % Palette.medicationHexes.size]
        }
    }
}
