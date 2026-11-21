// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.time.Instant
import java.util.UUID

/**
 * Where the logic writes. The app backs it with Room; tests keep everything in memory. The logic
 * changes the object graph (medications, their events) and calls [save] when a step is complete,
 * so the store persists the changed objects together.
 */
interface DoseStore {
    /** Name of this device for the record ("Pixel 9a"). */
    val deviceName: String

    /** Texts that end up in the activity log, in the app's language. */
    val texts: ActivityTexts

    /** A new event, already added to its medication's list. */
    fun inserted(event: DoseEvent)

    /** An event removed (undo of a dose). Already taken out of its medication's list. */
    fun deleted(event: DoseEvent)

    fun log(entry: ActivityEntry)

    /** Persist every change since the last save. */
    fun save()
}

/** Activity log details; the app fills them from string resources. */
interface ActivityTexts {
    /** "Recorded later, taken at 2 Nov 2026 8:05." */
    fun recordedLater(takenAt: Instant): String

    /** "Previously Taken." */
    fun previously(status: DoseStatus): String

    /** "10 tablets, was 4 tablets." or just "10 tablets" when the supply was not tracked before. */
    fun stockCounted(medication: Medication, count: Double, previous: Double?): String

    /** "Counted …, records expected …." plus "Recorded N missing doses as taken." */
    fun supplyCheck(medication: Medication, counted: Double, expected: Double, backfilled: Int): String
}

/** Notifications the logic asks for; the app implements them, tests record them. */
interface DoseNotifying {
    fun notifyLowStock(medication: Medication)

    /** A dose was confirmed or skipped: pending follow-up reminders for it can go. */
    fun doseResolved(medicationId: UUID, scheduledAt: Instant) {}

    /** A "before a meal" dose was taken: tell the person when they may eat. */
    fun scheduleMealReady(medicationId: UUID, name: String, scheduledAt: Instant, at: Instant, waitMinutes: Int) {}

    /** The dose was reset or skipped after all: the meal reminder for it goes away. */
    fun cancelMealReady(medicationId: UUID, scheduledAt: Instant) {}
}

object ActivityLogger {
    fun log(
        store: DoseStore,
        kind: ActivityKind,
        medication: Medication?,
        scheduledAt: Instant?,
        detail: String = "",
        source: DoseSource = DoseSource.APP,
        at: Instant,
    ): ActivityEntry {
        val entry = ActivityEntry(
            date = at,
            kind = kind,
            medicationId = medication?.id,
            medicationName = medication?.name ?: "",
            scheduledAt = scheduledAt,
            detail = detail,
            deviceName = store.deviceName,
            source = source,
        )
        store.log(entry)
        return entry
    }
}
