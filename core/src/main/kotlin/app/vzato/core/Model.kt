// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.time.Instant
import java.util.UUID

// The object graph the logic works on, mirroring the iOS Core Data model (version 8): the same
// fields, enums stored as raw strings, lists as "1,2,3". The app loads it from Room and saves the
// changed objects through DoseStore; tests keep it in memory.

enum class DoseStatus(val raw: String) {
    PENDING("pending"), TAKEN("taken"), SKIPPED("skipped"), MISSED("missed");

    /** Merge priority across devices: a taken dose must never be hidden by a skip. */
    val mergeRank: Int
        get() = when (this) {
            TAKEN -> 3
            SKIPPED -> 2
            MISSED -> 1
            PENDING -> 0
        }

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw } ?: PENDING
    }
}

enum class DoseSource(val raw: String) {
    APP("app"), NOTIFICATION("notification"), WIDGET("widget"), HEALTH("health"),
    RECONCILIATION("reconciliation"), CAREGIVER("caregiver");

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw } ?: APP
    }
}

enum class MealRelation(val raw: String) {
    ANY("any"), BEFORE("before"), WITH("with"), AFTER("after");

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw } ?: ANY
    }
}

enum class RepeatKind(val raw: String) {
    /** On chosen weekdays (every day by default). */
    WEEKDAYS("weekdays"),

    /** Every N days, weeks or months, counted from the start date. */
    DAYS("days"), WEEKS("weeks"), MONTHS("months");

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw } ?: WEEKDAYS
    }
}

enum class ProfileKind(val raw: String) {
    PERSON("person"), PET("pet");

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw } ?: PERSON
    }
}

enum class ActivityKind(val raw: String) {
    TAKEN("taken"), SKIPPED("skipped"), RESET("reset"), EXTRA_DOSE("extraDose"),
    STOCK_COUNTED("stockCounted"), SUPPLY_CHECK("supplyCheck"), DUPLICATE_MERGED("duplicateMerged");

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw } ?: TAKEN
    }
}

class Profile(
    val id: UUID = UUID.randomUUID(),
    var name: String = "",
    var kind: ProfileKind = ProfileKind.PERSON,
    var symbolName: String = "",
    var colorHex: String = "",
    var isDefault: Boolean = false,
    var createdAt: Instant? = null,
    var updatedAt: Instant? = null,
)

class Medication(
    val id: UUID = UUID.randomUUID(),
    var name: String = "",
    var dosageText: String = "",
    var unitsPerDose: Double = 1.0,
    var unit: DoseUnit = DoseUnit.TABLET,
    var notes: String = "",
    var colorHex: String = "",
    var createdAt: Instant? = null,
    var isArchived: Boolean = false,
    var archivedAt: Instant? = null,
    var tracksStock: Boolean = false,
    var stockCount: Double = 0.0,
    var lowStockThreshold: Double = 0.0,
    var stockCountedAt: Instant? = null,
    var stockAtLastCount: Double = 0.0,
    var lowStockNotifiedAt: Instant? = null,
    var profileId: UUID? = null,
    var updatedAt: Instant? = null,
) {
    val schedules: MutableList<Schedule> = mutableListOf()
    val events: MutableList<DoseEvent> = mutableListOf()

    /** Schedules that currently generate doses. */
    val activeSchedules: List<Schedule> get() = schedules.filter { !it.isPaused }

    val effectiveUnitsPerDose: Double get() = maxOf(MINIMUM_DOSE_UNITS, unitsPerDose)
    val isLowStock: Boolean get() = tracksStock && stockCount <= lowStockThreshold + 0.0001
    val isOutOfStock: Boolean get() = tracksStock && stockCount + 0.0001 < effectiveUnitsPerDose
    val dosesInStock: Int
        get() = if (tracksStock) kotlin.math.floor(stockCount / effectiveUnitsPerDose + 0.0001).toInt() else 0

    companion object {
        /** Smallest dose the app works with: a quarter tablet. */
        const val MINIMUM_DOSE_UNITS = 0.25
    }
}

class Schedule(
    val id: UUID = UUID.randomUUID(),
    /** Calendar weekdays as on iOS: 1 = Sunday … 7 = Saturday. */
    var weekdays: List<Int> = (1..7).toList(),
    /** Times of day as minutes since midnight. */
    var minutesOfDay: List<Int> = emptyList(),
    var mealRelation: MealRelation = MealRelation.ANY,
    /** For "before a meal": minutes to wait after the dose before eating. */
    var mealWaitMinutes: Int = 30,
    var startDate: Instant? = null,
    var endDate: Instant? = null,
    var isPaused: Boolean = false,
    var repeatKind: RepeatKind = RepeatKind.WEEKDAYS,
    /** N in "every N days / weeks / months"; ignored for weekdays. */
    var repeatIntervalCount: Int = 1,
    var updatedAt: Instant? = null,
) {
    val repeatInterval: Int get() = maxOf(1, repeatIntervalCount)
    val isEveryDay: Boolean get() = repeatKind == RepeatKind.WEEKDAYS && weekdays.toSet().size == 7
}

/** Exists only once the user acted on a dose; an untouched scheduled dose has no record. */
class DoseEvent(
    val id: UUID = UUID.randomUUID(),
    var medication: Medication? = null,
    var scheduledAt: Instant? = null,
    var status: DoseStatus = DoseStatus.PENDING,
    var takenAt: Instant? = null,
    var updatedAt: Instant? = null,
    var source: DoseSource = DoseSource.APP,
    var isUnscheduled: Boolean = false,
    var deviceName: String = "",
    /** The amount taken, in [unitRaw]; 0 for records without one (use [takenUnits]). */
    var units: Double = 0.0,
    var unitRaw: String = "",
) {
    /** What was taken: the saved amount, or the medication's current one for older records. */
    val takenUnits: Double
        get() = if (units > 0) units else (medication?.effectiveUnitsPerDose ?: Medication.MINIMUM_DOSE_UNITS)

    val takenUnit: DoseUnit
        get() = DoseUnit.entries.firstOrNull { it.raw == unitRaw } ?: medication?.unit ?: DoseUnit.TABLET

    /** Saves the medication's current amount on a record that has none yet. */
    fun keepAmount() {
        val medication = medication ?: return
        if (units > 0) return
        units = medication.effectiveUnitsPerDose
        unitRaw = medication.unit.raw
    }

    companion object {
        /**
         * When two devices recorded the same dose before syncing, this decides which record stays.
         * Must give the same answer on every device: taken beats skipped beats anything else, the
         * earliest intake wins, then the earliest write, then the record id.
         */
        fun preferred(events: List<DoseEvent>): DoseEvent? = events.minWithOrNull(
            compareByDescending<DoseEvent> { it.status.mergeRank }
                .thenBy { it.takenAt ?: Instant.MAX }
                .thenBy { it.updatedAt ?: Instant.MAX }
                // iOS compares uuidString, which is upper case.
                .thenBy { it.id.toString().uppercase() },
        )
    }
}

/** One line of the activity log. Never edited or deleted. */
class ActivityEntry(
    val id: UUID = UUID.randomUUID(),
    val date: Instant,
    val kind: ActivityKind,
    val medicationId: UUID?,
    val medicationName: String,
    val scheduledAt: Instant?,
    val detail: String,
    val deviceName: String,
    val source: DoseSource,
)
