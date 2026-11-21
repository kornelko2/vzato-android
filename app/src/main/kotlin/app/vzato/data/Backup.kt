// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The backup file: one JSON object with every table, the field names of the iOS data model
 * (docs/backup-format.md), so the iOS app can read it later. Moments are epoch milliseconds,
 * UUIDs strings, enums raw strings, lists "1,2,3".
 */
object Backup {
    const val FORMAT = "vzato-backup"
    const val VERSION = 1

    fun export(dao: VzatoDao, appVersion: String, exportedAt: Long): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("app", "android $appVersion")
        .put("exportedAt", exportedAt)
        .put("profiles", JSONArray(dao.profiles().map { json(it) }))
        .put("medications", JSONArray(dao.medications().map { json(it) }))
        .put("schedules", JSONArray(dao.schedules().map { json(it) }))
        .put("doseEvents", JSONArray(dao.events().map { json(it) }))
        .put("activity", JSONArray(dao.allActivity().map { json(it) }))
        .toString(1)

    /** Replaces everything with the file's contents. Throws on a file that is not a Vzato backup. */
    fun restore(dao: VzatoDao, text: String) {
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "not a Vzato backup" }
        require(root.optInt("version") in 1..VERSION) { "backup from a newer version" }
        val profiles = root.objects("profiles").map {
            ProfileEntity(it.getString("id"), it.optString("name"), it.optString("kindRaw", "person"), it.optString("symbolName"),
                it.optString("colorHex"), it.optBoolean("isDefault"), it.long("createdAt"), it.long("updatedAt"))
        }
        val medications = root.objects("medications").map {
            MedicationEntity(
                it.getString("id"), it.str("profileId"), it.optString("name"), it.optString("dosageText"), it.optDouble("unitsPerDose", 1.0),
                it.optString("unitKindRaw", "tablet"), it.optString("notes"), it.optString("colorHex"), it.long("createdAt"),
                it.optBoolean("isArchived"), it.long("archivedAt"), it.optBoolean("tracksStock"), it.optDouble("stockCount", 0.0),
                it.optDouble("lowStockThreshold", 0.0), it.long("stockCountedAt"), it.optDouble("stockAtLastCount", 0.0),
                it.long("lowStockNotifiedAt"), it.optString("organizerModeRaw"), it.str("shownPhotoKindRaw"), it.str("registryCode"),
                it.str("registrySourceRaw"), it.long("updatedAt"),
            )
        }
        val schedules = root.objects("schedules").map {
            ScheduleEntity(
                it.getString("id"), it.getString("medicationId"), it.optString("weekdaysRaw", "1,2,3,4,5,6,7"), it.optString("minutesRaw"),
                it.optString("mealRelationRaw", "any"), it.optInt("mealWaitMinutes", 30), it.long("startDate"), it.long("endDate"),
                it.optBoolean("isPaused"), it.optString("repeatKindRaw", "weekdays"), it.optInt("repeatIntervalCount", 1), it.long("updatedAt"),
            )
        }
        val events = root.objects("doseEvents").map {
            DoseEventEntity(
                it.getString("id"), it.getString("medicationId"), it.long("scheduledAt"), it.optString("statusRaw", "pending"),
                it.long("takenAt"), it.long("updatedAt"), it.optString("sourceRaw", "app"), it.optBoolean("isUnscheduled"),
                it.optString("deviceName"), it.optDouble("units", 0.0), it.optString("unitRaw"),
            )
        }
        val activity = root.objects("activity").map {
            ActivityEntryEntity(
                it.getString("id"), it.getLong("date"), it.optString("kindRaw", "taken"), it.str("medicationId"), it.optString("medicationName"),
                it.long("scheduledAt"), it.optString("detail"), it.optString("deviceName"), it.optString("sourceRaw", "app"),
            )
        }
        dao.deleteAll()
        dao.upsertProfiles(profiles)
        dao.upsertMedications(medications)
        dao.upsertSchedules(schedules)
        dao.upsertEvents(events)
        dao.insertActivity(activity)
    }

    // Written out by hand, not by reflection: a release build renames fields.
    private fun json(p: ProfileEntity) = JSONObject().put("id", p.id).put("name", p.name).put("kindRaw", p.kindRaw)
        .put("symbolName", p.symbolName).put("colorHex", p.colorHex).put("isDefault", p.isDefault)
        .putOpt("createdAt", p.createdAt).putOpt("updatedAt", p.updatedAt)

    private fun json(m: MedicationEntity) = JSONObject().put("id", m.id).putOpt("profileId", m.profileId).put("name", m.name)
        .put("dosageText", m.dosageText).put("unitsPerDose", m.unitsPerDose).put("unitKindRaw", m.unitKindRaw).put("notes", m.notes)
        .put("colorHex", m.colorHex).putOpt("createdAt", m.createdAt).put("isArchived", m.isArchived).putOpt("archivedAt", m.archivedAt)
        .put("tracksStock", m.tracksStock).put("stockCount", m.stockCount).put("lowStockThreshold", m.lowStockThreshold)
        .putOpt("stockCountedAt", m.stockCountedAt).put("stockAtLastCount", m.stockAtLastCount)
        .putOpt("lowStockNotifiedAt", m.lowStockNotifiedAt).put("organizerModeRaw", m.organizerModeRaw)
        .putOpt("shownPhotoKindRaw", m.shownPhotoKindRaw).putOpt("registryCode", m.registryCode)
        .putOpt("registrySourceRaw", m.registrySourceRaw).putOpt("updatedAt", m.updatedAt)

    private fun json(s: ScheduleEntity) = JSONObject().put("id", s.id).put("medicationId", s.medicationId).put("weekdaysRaw", s.weekdaysRaw)
        .put("minutesRaw", s.minutesRaw).put("mealRelationRaw", s.mealRelationRaw).put("mealWaitMinutes", s.mealWaitMinutes)
        .putOpt("startDate", s.startDate).putOpt("endDate", s.endDate).put("isPaused", s.isPaused).put("repeatKindRaw", s.repeatKindRaw)
        .put("repeatIntervalCount", s.repeatIntervalCount).putOpt("updatedAt", s.updatedAt)

    private fun json(e: DoseEventEntity) = JSONObject().put("id", e.id).put("medicationId", e.medicationId).putOpt("scheduledAt", e.scheduledAt)
        .put("statusRaw", e.statusRaw).putOpt("takenAt", e.takenAt).putOpt("updatedAt", e.updatedAt).put("sourceRaw", e.sourceRaw)
        .put("isUnscheduled", e.isUnscheduled).put("deviceName", e.deviceName).put("units", e.units).put("unitRaw", e.unitRaw)

    private fun json(a: ActivityEntryEntity) = JSONObject().put("id", a.id).put("date", a.date).put("kindRaw", a.kindRaw)
        .putOpt("medicationId", a.medicationId).put("medicationName", a.medicationName).putOpt("scheduledAt", a.scheduledAt)
        .put("detail", a.detail).put("deviceName", a.deviceName).put("sourceRaw", a.sourceRaw)

    private fun JSONObject.objects(key: String): List<JSONObject> =
        optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()

    private fun JSONObject.long(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
    private fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
