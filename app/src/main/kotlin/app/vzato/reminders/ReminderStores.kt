// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.reminders

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** One dose in a reminder: which medication, at which minute of the day. */
data class Dose(val medicationId: UUID, val minutes: Int)

/** The doses one notification stands for, on one day ("yyyy-MM-dd"). */
data class DoseGroup(val dayKey: String, val minutesOfDay: Int, val doses: List<Dose>) {
    val groupKey: String get() = "$dayKey|$minutesOfDay"

    fun encode(): String = "$dayKey;$minutesOfDay;" + doses.joinToString(",") { "${it.medicationId}:${it.minutes}" }

    companion object {
        fun decode(text: String?): DoseGroup? {
            val parts = text?.split(";") ?: return null
            if (parts.size != 3) return null
            val minutes = parts[1].toIntOrNull() ?: return null
            val doses = parts[2].split(",").filter { it.isNotBlank() }.mapNotNull { item ->
                val (id, m) = item.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
                runCatching { Dose(UUID.fromString(id), m.toInt()) }.getOrNull()
            }
            return DoseGroup(parts[0], minutes, doses)
        }
    }
}

/** A snoozed reminder: comes back at [fireAt], then repeats like a normal one ([attempt] counts). */
data class Snooze(val group: DoseGroup, val fireAt: Instant, val attempt: Int)

/** "You can eat now" after a "before a meal" dose. */
data class MealReady(val medicationId: UUID, val scheduledAt: Instant, val readyAt: Instant, val name: String, val waitMinutes: Int)

/**
 * The little the reminders keep between alarms, on this device only: snoozes, meal reminders,
 * when the last alarm was handled, and which run-out warnings already went off.
 */
class ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    var lastCheck: Instant?
        get() = prefs.getLong(LAST_CHECK, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(value) = prefs.edit { if (value == null) remove(LAST_CHECK) else putLong(LAST_CHECK, value.toEpochMilli()) }

    var snoozes: List<Snooze>
        get() = array(SNOOZES).mapNotNull { o ->
            DoseGroup.decode(o.optString("group"))?.let { Snooze(it, Instant.ofEpochMilli(o.getLong("fireAt")), o.optInt("attempt")) }
        }
        set(value) = put(SNOOZES, value.map { JSONObject().put("group", it.group.encode()).put("fireAt", it.fireAt.toEpochMilli()).put("attempt", it.attempt) })

    var meals: List<MealReady>
        get() = array(MEALS).mapNotNull { o ->
            runCatching {
                MealReady(
                    UUID.fromString(o.getString("id")), Instant.ofEpochMilli(o.getLong("scheduledAt")),
                    Instant.ofEpochMilli(o.getLong("readyAt")), o.optString("name"), o.optInt("wait"),
                )
            }.getOrNull()
        }
        set(value) = put(
            MEALS,
            value.map {
                JSONObject().put("id", it.medicationId.toString()).put("scheduledAt", it.scheduledAt.toEpochMilli())
                    .put("readyAt", it.readyAt.toEpochMilli()).put("name", it.name).put("wait", it.waitMinutes)
            },
        )

    /** Run-out warnings already planned or shown, per medication (RunOutWarning). */
    fun runOutFire(medicationId: UUID): Instant? =
        prefs.getLong("runout|$medicationId", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    fun recordRunOut(medicationId: UUID, fire: Instant) = prefs.edit { putLong("runout|$medicationId", fire.toEpochMilli()) }

    private fun array(key: String): List<JSONObject> {
        val text = prefs.getString(key, null) ?: return emptyList()
        return runCatching { JSONArray(text).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } }.getOrDefault(emptyList())
    }

    private fun put(key: String, items: List<JSONObject>) = prefs.edit { putString(key, JSONArray(items).toString()) }

    private companion object {
        const val LAST_CHECK = "lastCheck"
        const val SNOOZES = "snoozes"
        const val MEALS = "meals"
    }
}
