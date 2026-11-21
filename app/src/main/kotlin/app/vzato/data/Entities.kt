// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Tables mirror the iOS Core Data model, version 8: the same field names, enums as raw strings,
// lists as "1,2,3", so a backup file can later move between the two apps. UUIDs are stored as
// strings, moments as epoch milliseconds. No foreign keys: records may later arrive from a sync
// in any order; deleting a medication removes its records in VzatoDao.deleteMedication.

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kindRaw: String,
    val symbolName: String,
    val colorHex: String,
    val isDefault: Boolean,
    val createdAt: Long?,
    val updatedAt: Long?,
)

@Entity(tableName = "medication", indices = [Index("profileId")])
data class MedicationEntity(
    @PrimaryKey val id: String,
    val profileId: String?,
    val name: String,
    val dosageText: String,
    val unitsPerDose: Double,
    val unitKindRaw: String,
    val notes: String,
    val colorHex: String,
    val createdAt: Long?,
    val isArchived: Boolean,
    val archivedAt: Long?,
    val tracksStock: Boolean,
    val stockCount: Double,
    val lowStockThreshold: Double,
    val stockCountedAt: Long?,
    val stockAtLastCount: Double,
    val lowStockNotifiedAt: Long?,
    val organizerModeRaw: String,
    val shownPhotoKindRaw: String?,
    val registryCode: String?,
    val registrySourceRaw: String?,
    val updatedAt: Long?,
)

@Entity(tableName = "schedule", indices = [Index("medicationId")])
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    val weekdaysRaw: String,
    val minutesRaw: String,
    val mealRelationRaw: String,
    val mealWaitMinutes: Int,
    val startDate: Long?,
    val endDate: Long?,
    val isPaused: Boolean,
    val repeatKindRaw: String,
    val repeatIntervalCount: Int,
    val updatedAt: Long?,
)

@Entity(tableName = "doseEvent", indices = [Index("medicationId"), Index("scheduledAt")])
data class DoseEventEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    val scheduledAt: Long?,
    val statusRaw: String,
    val takenAt: Long?,
    val updatedAt: Long?,
    val sourceRaw: String,
    val isUnscheduled: Boolean,
    val deviceName: String,
    val units: Double,
    val unitRaw: String,
)

/** The activity log. Rows are only ever inserted. */
@Entity(tableName = "activityEntry", indices = [Index("date")])
data class ActivityEntryEntity(
    @PrimaryKey val id: String,
    val date: Long,
    val kindRaw: String,
    val medicationId: String?,
    val medicationName: String,
    val scheduledAt: Long?,
    val detail: String,
    val deviceName: String,
    val sourceRaw: String,
)
