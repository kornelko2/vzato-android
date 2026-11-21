// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Records what the user did with a dose. The only place where the supply changes because of a
 * dose, and every action writes an activity entry.
 */
class DoseLogger(
    private val store: DoseStore,
    private val now: Instant,
    private val notifier: DoseNotifying,
    private val zone: ZoneId,
) {
    fun markTaken(slot: DoseSlot, at: Instant? = null, source: DoseSource = DoseSource.APP): DoseEvent {
        val event = slot.event ?: makeEvent(slot)
        set(event, DoseStatus.TAKEN, at ?: now, source)
        return event
    }

    fun markSkipped(slot: DoseSlot, source: DoseSource = DoseSource.APP): DoseEvent {
        val event = slot.event ?: makeEvent(slot)
        set(event, DoseStatus.SKIPPED, null, source)
        return event
    }

    /** Removes the record so the slot is pending again (or missed, once the grace period passed). */
    fun reset(slot: DoseSlot, source: DoseSource = DoseSource.APP) {
        val event = slot.event ?: return
        val medication = event.medication
        ActivityLogger.log(
            store, ActivityKind.RESET, medication, event.scheduledAt,
            store.texts.previously(event.status), source, now,
        )
        adjustStock(event, DoseStatus.PENDING)
        if (medication != null) {
            event.scheduledAt?.let { notifier.cancelMealReady(medication.id, it) }
            medication.events.remove(event)
            medication.updatedAt = now
        }
        store.deleted(event)
        store.save()
        medication?.let { StockService.checkLowStock(it, now, notifier) }
    }

    /** A dose outside the plan, e.g. a painkiller. Counted in history and supply. */
    fun logExtraDose(medication: Medication, at: Instant, source: DoseSource = DoseSource.APP): DoseEvent {
        val event = DoseEvent(medication = medication, scheduledAt = at, isUnscheduled = true, updatedAt = now)
        medication.events += event
        store.inserted(event)
        set(event, DoseStatus.TAKEN, at, source, ActivityKind.EXTRA_DOSE)
        return event
    }

    private fun set(event: DoseEvent, status: DoseStatus, takenAt: Instant?, source: DoseSource, kind: ActivityKind? = null) {
        adjustStock(event, status)
        event.status = status
        event.takenAt = if (status == DoseStatus.TAKEN) takenAt else null
        event.updatedAt = now
        event.source = source
        event.deviceName = store.deviceName
        val logKind = kind ?: if (status == DoseStatus.TAKEN) ActivityKind.TAKEN else ActivityKind.SKIPPED
        val detail = if (status == DoseStatus.TAKEN && takenAt != null && Duration.between(takenAt, now).abs().seconds > 60) {
            store.texts.recordedLater(takenAt)
        } else {
            ""
        }
        ActivityLogger.log(store, logKind, event.medication, event.scheduledAt, detail, source, now)
        event.medication?.updatedAt = now
        store.save()
        val medication = event.medication ?: return
        StockService.checkLowStock(medication, now, notifier)
        val scheduledAt = event.scheduledAt ?: return
        if (status == DoseStatus.PENDING) return
        notifier.doseResolved(medication.id, scheduledAt)
        notifier.cancelMealReady(medication.id, scheduledAt)
        // Before a meal: remind when eating is fine. Not for doses recorded too late to matter.
        val readyAt = DoseScheduler.mealReadyTime(event, zone) ?: return
        if (readyAt <= now) return
        val wait = DoseScheduler.schedule(medication, scheduledAt, zone)?.mealWaitMinutes ?: return
        notifier.scheduleMealReady(medication.id, medication.name, scheduledAt, readyAt, wait)
    }

    /** A dose becoming taken saves the amount it takes now; undoing it returns exactly that amount. */
    private fun adjustStock(event: DoseEvent, newStatus: DoseStatus) {
        val medication = event.medication ?: return
        val was = event.status == DoseStatus.TAKEN
        val will = newStatus == DoseStatus.TAKEN
        if (!was && will) {
            event.units = medication.effectiveUnitsPerDose
            event.unitRaw = medication.unit.raw
            if (medication.tracksStock) medication.stockCount = maxOf(0.0, medication.stockCount - event.units)
        } else if (was && !will) {
            // Only in the same unit: a dose taken in drops says nothing about a supply now in ml.
            if (medication.tracksStock && event.takenUnit == medication.unit) medication.stockCount += event.takenUnits
            event.units = 0.0
            event.unitRaw = ""
        }
    }

    private fun makeEvent(slot: DoseSlot): DoseEvent {
        val event = DoseEvent(medication = slot.medication, scheduledAt = slot.scheduledAt, updatedAt = now)
        slot.medication.events += event
        store.inserted(event)
        return event
    }

    companion object {
        /** Before a medication's amount or unit changes: doses taken with the old amount keep it. */
        fun keepAmounts(medication: Medication) {
            medication.events.filter { it.status == DoseStatus.TAKEN && it.units <= 0 }.forEach { it.keepAmount() }
        }
    }
}
