// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Does the counted supply match the records? */
data class ReconciliationReport(
    val medication: Medication,
    val since: Instant,
    val scheduledDoses: Int,
    val recordedTaken: Int,
    val recordedSkipped: Int,
    val expectedStock: Double,
    val actualStock: Double?,
) {
    sealed interface Verdict {
        data object NotTracking : Verdict
        data object NeedsCount : Verdict
        data object Consistent : Verdict

        /** More pills are gone than recorded: doses were taken without being confirmed. */
        data class UnrecordedDoses(val count: Int) : Verdict

        /** Fewer pills are gone than recorded: confirmed doses may not have been taken. */
        data class OverRecordedDoses(val count: Int) : Verdict
    }

    /** Scheduled doses with no record at all (missed or still pending). */
    val unaccounted: Int get() = maxOf(0, scheduledDoses - recordedTaken - recordedSkipped)
    val unitDifference: Double? get() = actualStock?.let { expectedStock - it }

    val doseDifference: Int?
        get() = unitDifference?.let { roundHalfAwayFromZero(it / medication.effectiveUnitsPerDose) }

    val verdict: Verdict
        get() {
            if (!medication.tracksStock) return Verdict.NotTracking
            val difference = doseDifference ?: return Verdict.NeedsCount
            return when {
                difference > 0 -> Verdict.UnrecordedDoses(difference)
                difference < 0 -> Verdict.OverRecordedDoses(-difference)
                else -> Verdict.Consistent
            }
        }

    private companion object {
        /** Swift's rounded() rounds half away from zero; Kotlin's roundToInt rounds half up. */
        fun roundHalfAwayFromZero(value: Double): Int =
            if (value < 0) -((-value).roundToInt()) else value.roundToInt()
    }
}

object ReconciliationService {
    fun report(medication: Medication, actualStock: Double?, now: Instant, zone: ZoneId): ReconciliationReport {
        val since = medication.stockCountedAt ?: medication.createdAt ?: now
        val from = minOf(since, now)
        fun inside(at: Instant) = at >= from && at <= now
        val scheduled = DoseScheduler.slots(listOf(medication), from, now, zone).count { !it.isUnscheduled }
        val taken = medication.events.count { it.status == DoseStatus.TAKEN && inside(it.takenAt ?: it.scheduledAt ?: now) }
        val skipped = medication.events.count { event ->
            event.status == DoseStatus.SKIPPED && event.scheduledAt?.let(::inside) == true
        }
        val units = medication.effectiveUnitsPerDose
        val expected = if (medication.tracksStock) maxOf(0.0, medication.stockAtLastCount - taken * units) else 0.0
        return ReconciliationReport(medication, since, scheduled, taken, skipped, expected, actualStock)
    }

    /**
     * Saves the counted supply. Optionally marks the oldest unaccounted scheduled doses as taken
     * (source: supply check) to match the count. Never removes records.
     */
    fun apply(
        report: ReconciliationReport,
        actualStock: Double,
        recordMissingDoses: Boolean,
        store: DoseStore,
        now: Instant,
        zone: ZoneId,
        notifier: DoseNotifying,
    ) {
        val medication = report.medication
        var backfilled = 0
        val verdict = report.copy(actualStock = actualStock).verdict
        if (recordMissingDoses && verdict is ReconciliationReport.Verdict.UnrecordedDoses) {
            val open = DoseScheduler.slots(listOf(medication), minOf(report.since, now), now, zone)
                .filter { it.event == null }
                .take(verdict.count)
            val logger = DoseLogger(store, now, notifier, zone)
            for (slot in open) {
                logger.markTaken(slot, slot.scheduledAt, DoseSource.RECONCILIATION)
                backfilled++
            }
        }
        val detail = store.texts.supplyCheck(medication, actualStock, report.expectedStock, backfilled)
        ActivityLogger.log(store, ActivityKind.SUPPLY_CHECK, medication, null, detail, DoseSource.RECONCILIATION, now)
        StockService.setStock(medication, actualStock, now, store, log = false)
        store.save()
    }
}
