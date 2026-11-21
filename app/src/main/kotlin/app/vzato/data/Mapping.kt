// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.data

import app.vzato.core.ActivityEntry
import app.vzato.core.DoseEvent
import app.vzato.core.DoseSource
import app.vzato.core.DoseStatus
import app.vzato.core.DoseUnit
import app.vzato.core.MealRelation
import app.vzato.core.Medication
import app.vzato.core.Profile
import app.vzato.core.ProfileKind
import app.vzato.core.RepeatKind
import app.vzato.core.Schedule
import app.vzato.core.ScheduleRules
import java.time.Instant
import java.util.UUID

// Rows ↔ the core object graph.

internal fun Long?.instant(): Instant? = this?.let(Instant::ofEpochMilli)
internal fun Instant?.millis(): Long? = this?.toEpochMilli()
internal fun String.uuid(): UUID = UUID.fromString(this)

fun ProfileEntity.toCore() = Profile(
    id = id.uuid(), name = name, kind = ProfileKind.fromRaw(kindRaw), symbolName = symbolName,
    colorHex = colorHex, isDefault = isDefault, createdAt = createdAt.instant(), updatedAt = updatedAt.instant(),
)

fun Profile.toEntity() = ProfileEntity(
    id.toString(), name, kind.raw, symbolName, colorHex, isDefault, createdAt.millis(), updatedAt.millis(),
)

fun MedicationEntity.toCore() = Medication(
    id = id.uuid(), name = name, dosageText = dosageText, unitsPerDose = unitsPerDose,
    unit = DoseUnit.fromRaw(unitKindRaw), notes = notes, colorHex = colorHex, createdAt = createdAt.instant(),
    isArchived = isArchived, archivedAt = archivedAt.instant(), tracksStock = tracksStock, stockCount = stockCount,
    lowStockThreshold = lowStockThreshold, stockCountedAt = stockCountedAt.instant(), stockAtLastCount = stockAtLastCount,
    lowStockNotifiedAt = lowStockNotifiedAt.instant(), profileId = profileId?.uuid(), updatedAt = updatedAt.instant(),
    organizerModeRaw = organizerModeRaw, shownPhotoKindRaw = shownPhotoKindRaw, registryCode = registryCode,
    registrySourceRaw = registrySourceRaw,
)

fun Medication.toEntity() = MedicationEntity(
    id = id.toString(), profileId = profileId?.toString(), name = name, dosageText = dosageText,
    unitsPerDose = unitsPerDose, unitKindRaw = unit.raw, notes = notes, colorHex = colorHex,
    createdAt = createdAt.millis(), isArchived = isArchived, archivedAt = archivedAt.millis(),
    tracksStock = tracksStock, stockCount = stockCount, lowStockThreshold = lowStockThreshold,
    stockCountedAt = stockCountedAt.millis(), stockAtLastCount = stockAtLastCount,
    lowStockNotifiedAt = lowStockNotifiedAt.millis(), organizerModeRaw = organizerModeRaw,
    shownPhotoKindRaw = shownPhotoKindRaw, registryCode = registryCode, registrySourceRaw = registrySourceRaw,
    updatedAt = updatedAt.millis(),
)

fun ScheduleEntity.toCore() = Schedule(
    id = id.uuid(), weekdays = ScheduleRules.weekdays(weekdaysRaw), minutesOfDay = ScheduleRules.minutes(minutesRaw),
    mealRelation = MealRelation.fromRaw(mealRelationRaw), mealWaitMinutes = mealWaitMinutes,
    startDate = startDate.instant(), endDate = endDate.instant(), isPaused = isPaused,
    repeatKind = RepeatKind.fromRaw(repeatKindRaw), repeatIntervalCount = repeatIntervalCount, updatedAt = updatedAt.instant(),
)

fun Schedule.toEntity(medicationId: UUID) = ScheduleEntity(
    id = id.toString(), medicationId = medicationId.toString(), weekdaysRaw = ScheduleRules.encode(weekdays),
    minutesRaw = ScheduleRules.encode(minutesOfDay), mealRelationRaw = mealRelation.raw, mealWaitMinutes = mealWaitMinutes,
    startDate = startDate.millis(), endDate = endDate.millis(), isPaused = isPaused, repeatKindRaw = repeatKind.raw,
    repeatIntervalCount = repeatIntervalCount, updatedAt = updatedAt.millis(),
)

fun DoseEventEntity.toCore(medication: Medication) = DoseEvent(
    id = id.uuid(), medication = medication, scheduledAt = scheduledAt.instant(), status = DoseStatus.fromRaw(statusRaw),
    takenAt = takenAt.instant(), updatedAt = updatedAt.instant(), source = DoseSource.fromRaw(sourceRaw),
    isUnscheduled = isUnscheduled, deviceName = deviceName, units = units, unitRaw = unitRaw,
)

fun DoseEvent.toEntity(medicationId: UUID) = DoseEventEntity(
    id = id.toString(), medicationId = medicationId.toString(), scheduledAt = scheduledAt.millis(), statusRaw = status.raw,
    takenAt = takenAt.millis(), updatedAt = updatedAt.millis(), sourceRaw = source.raw, isUnscheduled = isUnscheduled,
    deviceName = deviceName, units = units, unitRaw = unitRaw,
)

fun ActivityEntry.toEntity() = ActivityEntryEntity(
    id = id.toString(), date = date.toEpochMilli(), kindRaw = kind.raw, medicationId = medicationId?.toString(),
    medicationName = medicationName, scheduledAt = scheduledAt.millis(), detail = detail, deviceName = deviceName,
    sourceRaw = source.raw,
)

fun ActivityEntryEntity.toCore() = ActivityEntry(
    id = id.uuid(), date = Instant.ofEpochMilli(date), kind = app.vzato.core.ActivityKind.fromRaw(kindRaw),
    medicationId = medicationId?.uuid(), medicationName = medicationName, scheduledAt = scheduledAt.instant(),
    detail = detail, deviceName = deviceName, source = DoseSource.fromRaw(sourceRaw),
)
