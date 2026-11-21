// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Every dose as a table, the same columns as the iOS export. */
object CsvExport {
    const val HEADER = "medication,dosage,scheduled_at,status,taken_at,source,unscheduled,profile,amount,unit"

    /**
     * Recorded doses plus the missed ones, which have no record (a scheduled dose gets one only when
     * the person acts), so the export shows the same history as the calendar.
     */
    fun make(events: List<DoseEvent>, missed: List<DoseSlot> = emptyList(), profileName: (UUID?) -> String = { "" }): String {
        val rows = mutableListOf<Pair<Instant, String>>()
        for (slot in missed) {
            rows += slot.scheduledAt to listOf(
                escape(slot.medication.name), escape(slot.medication.dosageText), iso(slot.scheduledAt),
                DoseStatus.MISSED.raw, "", "", "0", escape(profileName(slot.medication.profileId)), "", "",
            ).joinToString(",")
        }
        for (event in events) {
            val taken = event.status == DoseStatus.TAKEN
            rows += (event.scheduledAt ?: Instant.MIN) to listOf(
                escape(event.medication?.name ?: ""), escape(event.medication?.dosageText ?: ""),
                event.scheduledAt?.let(::iso) ?: "", event.status.raw, event.takenAt?.let(::iso) ?: "",
                event.source.raw, if (event.isUnscheduled) "1" else "0", escape(profileName(event.medication?.profileId)),
                if (taken) amountNumber(event.takenUnits) else "", if (taken) event.takenUnit.raw else "",
            ).joinToString(",")
        }
        return (listOf(HEADER) + rows.sortedBy { it.first }.map { it.second }).joinToString("\n") + "\n"
    }

    /** Scheduled doses without a record whose grace period is over, at most `maxDays` back. */
    fun missedSlots(medications: List<Medication>, now: Instant, grace: Duration, zone: ZoneId, maxDays: Int = 730): List<DoseSlot> {
        val earliest = medications.flatMap { it.activeSchedules }.mapNotNull { it.startDate }.minOrNull()
            ?: medications.mapNotNull { it.createdAt }.minOrNull() ?: now
        val limit = now.atZone(zone).minusDays(maxDays.toLong()).toInstant()
        val start = maxOf(earliest, limit).day(zone).start(zone)
        if (start >= now) return emptyList()
        return DoseScheduler.slots(medications, start, now, zone)
            .filter { !it.isUnscheduled && it.status(now, grace) == DoseStatus.MISSED }
    }

    /** "0.5": a plain number, the same in every language, so spreadsheets read it. */
    fun amountNumber(value: Double): String =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    fun escape(field: String): String {
        if (field.none { it == ',' || it == '"' || it == '\n' }) return field
        return "\"" + field.replace("\"", "\"\"") + "\""
    }

    /** ISO 8601 in UTC, as ISO8601DateFormatter writes it on iOS. */
    private fun iso(at: Instant): String = DateTimeFormatter.ISO_INSTANT.format(at.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
}
