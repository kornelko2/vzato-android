// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * Blocking DAO: the repository calls it on its own IO thread inside one transaction per edit, so
 * the core logic (plain Kotlin, not suspending) can save in the middle of a step.
 */
@Dao
interface VzatoDao {
    @Query("SELECT * FROM profile") fun profiles(): List<ProfileEntity>
    @Query("SELECT * FROM medication") fun medications(): List<MedicationEntity>
    @Query("SELECT * FROM schedule") fun schedules(): List<ScheduleEntity>
    @Query("SELECT * FROM doseEvent") fun events(): List<DoseEventEntity>

    @Query("SELECT * FROM activityEntry ORDER BY date DESC LIMIT :limit")
    fun activity(limit: Int): List<ActivityEntryEntity>

    @Upsert fun upsertProfiles(rows: List<ProfileEntity>)
    @Upsert fun upsertMedications(rows: List<MedicationEntity>)
    @Upsert fun upsertSchedules(rows: List<ScheduleEntity>)
    @Upsert fun upsertEvents(rows: List<DoseEventEntity>)
    @Insert fun insertActivity(rows: List<ActivityEntryEntity>)

    @Query("SELECT * FROM activityEntry ORDER BY date") fun allActivity(): List<ActivityEntryEntity>

    /** Everything, for restoring a backup. */
    @Transaction
    fun deleteAll() {
        deleteAllEvents(); deleteAllSchedules(); deleteAllMedications(); deleteAllProfiles(); deleteAllActivity()
    }

    @Query("DELETE FROM doseEvent") fun deleteAllEvents()
    @Query("DELETE FROM schedule") fun deleteAllSchedules()
    @Query("DELETE FROM medication") fun deleteAllMedications()
    @Query("DELETE FROM profile") fun deleteAllProfiles()
    @Query("DELETE FROM activityEntry") fun deleteAllActivity()

    @Query("DELETE FROM doseEvent WHERE id IN (:ids)") fun deleteEvents(ids: List<String>)
    @Query("DELETE FROM schedule WHERE id IN (:ids)") fun deleteSchedules(ids: List<String>)

    /** A medication and everything that belongs to it, as the iOS model's cascade delete. */
    @Transaction
    fun deleteMedication(id: String) {
        deleteEventsOf(id)
        deleteSchedulesOf(id)
        deleteMedicationRow(id)
    }

    @Query("DELETE FROM doseEvent WHERE medicationId = :id") fun deleteEventsOf(id: String)
    @Query("DELETE FROM schedule WHERE medicationId = :id") fun deleteSchedulesOf(id: String)
    @Query("DELETE FROM medication WHERE id = :id") fun deleteMedicationRow(id: String)
}

@Database(
    entities = [ProfileEntity::class, MedicationEntity::class, ScheduleEntity::class, DoseEventEntity::class, ActivityEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class VzatoDatabase : RoomDatabase() {
    abstract fun dao(): VzatoDao

    companion object {
        const val FILE_NAME = "vzato.db"

        fun open(context: Context): VzatoDatabase =
            Room.databaseBuilder(context.applicationContext, VzatoDatabase::class.java, FILE_NAME).build()

        fun inMemory(context: Context): VzatoDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, VzatoDatabase::class.java).build()
    }
}
