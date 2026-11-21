// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

object MedicationArchive {
    fun archive(medication: Medication, now: Instant) {
        medication.isArchived = true
        medication.archivedAt = now
        medication.updatedAt = now
    }

    /**
     * Back to the active list. Doses from the archived time were never asked for, so they must not
     * show as missed: the medication counts as added again now. Earlier records stay in history.
     */
    fun restore(medication: Medication, now: Instant) {
        medication.isArchived = false
        medication.archivedAt = null
        medication.createdAt = now
        medication.updatedAt = now
    }
}

/** Amounts typed by people: "10", "10,5", "10.5", "1½", "½". */
object AmountParser {
    fun parse(text: String): Double? {
        var cleaned = text.trim().replace(" ", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        val fractions = mapOf('¼' to 0.25, '½' to 0.5, '¾' to 0.75)
        var extra = 0.0
        fractions[cleaned.last()]?.let {
            extra = it
            cleaned = cleaned.dropLast(1)
            if (cleaned.isEmpty()) return extra
        }
        val value = cleaned.replace(",", ".").toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 0) return null
        return value + extra
    }

    /** The inverse for a text field: "10", "10,5" in Czech, "10.5" in English. */
    fun format(value: Double, locale: Locale = Locale.getDefault()): String =
        DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale)).apply { isGroupingUsed = false }.format(value)
}

object RunOutWarning {
    /**
     * A warning that already went off is not repeated until the supply lasts clearly longer again
     * (a refill): the new warning must be more than `windowDays` after the previous one.
     */
    fun shouldSchedule(fire: Instant, previous: Instant?, now: Instant, windowDays: Int, zone: ZoneId): Boolean {
        if (previous == null || previous > now) return true
        val limit = previous.atZone(zone).plusDays(windowDays.toLong()).toInstant()
        return fire > limit
    }
}
