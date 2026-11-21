// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The supply: counting, the low-supply warning and when it runs out. */
object StockService {
    /** The user counted (or bought) pills: this becomes the new baseline for the supply check. */
    fun setStock(medication: Medication, count: Double, at: Instant, store: DoseStore?, log: Boolean = true) {
        if (log && store != null) {
            val detail = store.texts.stockCounted(medication, maxOf(0.0, count), medication.stockCount.takeIf { medication.tracksStock })
            ActivityLogger.log(store, ActivityKind.STOCK_COUNTED, medication, null, detail, at = at)
        }
        medication.tracksStock = true
        medication.stockCount = maxOf(0.0, count)
        medication.stockAtLastCount = medication.stockCount
        medication.stockCountedAt = at
        medication.updatedAt = at
        if (!medication.isLowStock) medication.lowStockNotifiedAt = null
    }

    /** One low-supply notification per drop below the threshold. */
    fun checkLowStock(medication: Medication, now: Instant, notifier: DoseNotifying) {
        if (!medication.isLowStock) {
            medication.lowStockNotifiedAt = null
            return
        }
        if (medication.lowStockNotifiedAt != null) return
        notifier.notifyLowStock(medication)
        medication.lowStockNotifiedAt = now
    }

    /** The day to get more: `warningDays` before the projected run-out, never in the past. */
    fun reorderDate(medication: Medication, now: Instant, zone: ZoneId, warningDays: Int): LocalDate? {
        val runOut = projectedRunOutDate(medication, now, zone) ?: return null
        val day = runOut.day(zone).minusDays(maxOf(0, warningDays).toLong())
        return maxOf(day, now.day(zone))
    }

    /**
     * Time of the first upcoming dose that the current supply cannot cover, or null when the supply
     * lasts through the horizon or nothing is scheduled.
     */
    fun projectedRunOutDate(medication: Medication, now: Instant, zone: ZoneId, horizonDays: Int = 365): Instant? {
        if (!medication.tracksStock) return null
        var remaining = medication.stockCount
        val units = medication.effectiveUnitsPerDose
        var day = now.day(zone)
        repeat(horizonDays) {
            val upcoming = DoseScheduler.slots(listOf(medication), day, zone)
                .filter { it.event == null && it.scheduledAt >= now }
            for (slot in upcoming) {
                if (remaining + 0.0001 < units) return slot.scheduledAt
                remaining -= units
            }
            day = day.plusDays(1)
        }
        return null
    }
}
