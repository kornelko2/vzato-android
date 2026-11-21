// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.vzato.MainActivity
import app.vzato.R
import app.vzato.core.Medication
import app.vzato.data.Amounts
import app.vzato.data.ReminderSound
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Builds and posts every notification Vzato shows. Texts come from the iOS translations. */
class Notifications(private val context: Context, private val settings: app.vzato.data.AppSettings) {
    private val manager = NotificationManagerCompat.from(context)
    private val res get() = context.resources

    /**
     * Android fixes a channel's sound when it is created, so each sound gets its own channel
     * ("doses_chime"); choosing another sound removes the old channel. The person's own changes
     * to the old channel (vibration, Do Not Disturb) are lost then, as Android has no other way.
     */
    fun createChannels(doseSound: ReminderSound, mealSound: ReminderSound) {
        val system = context.getSystemService(NotificationManager::class.java)
        val doses = doseChannel(doseSound)
        val meals = mealChannel(mealSound)
        system.notificationChannels
            .filter { (it.id.startsWith("doses") && it.id != doses) || (it.id.startsWith("meals") && it.id != meals) }
            .forEach { system.deleteNotificationChannel(it.id) }
        system.createNotificationChannels(
            listOf(
                NotificationChannel(doses, res.getString(R.string.medication_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setSound(soundUri(doseSound), audio)
                },
                NotificationChannel(meals, res.getString(R.string.you_can_eat_now), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(soundUri(mealSound), audio)
                },
                NotificationChannel(CHANNEL_SUPPLY, res.getString(R.string.supply), NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    /** The channel of medication reminders now, for its system settings (Do Not Disturb). */
    val currentDoseChannel: String get() = doseChannel(settings.doseSound)
    private val currentMealChannel: String get() = mealChannel(settings.mealSound)

    private val audio = android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun soundUri(sound: ReminderSound): android.net.Uri =
        sound.raw?.let { android.net.Uri.parse("android.resource://${context.packageName}/$it") }
            ?: android.provider.Settings.System.DEFAULT_NOTIFICATION_URI

    private fun doseChannel(sound: ReminderSound) = "${CHANNEL_DOSES}_${sound.key}"
    private fun mealChannel(sound: ReminderSound) = "${CHANNEL_MEALS}_${sound.key}"

    val allowed: Boolean
        get() = manager.areNotificationsEnabled() && (
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            )

    /** One reminder for every medication due at one time, with Taken, Snooze and Skip. */
    fun postDose(tag: String, group: DoseGroup, medications: List<Medication>, attempt: Int, totalAttempts: Int, snoozeMinutes: Int) {
        if (!allowed || medications.isEmpty()) return
        val lines = medications.map { m ->
            val amount = Amounts.text(res, m.effectiveUnitsPerDose, m.unit)
            if (m.dosageText.isBlank()) "${m.name} · $amount" else "${m.name} · $amount (${m.dosageText})"
        }
        val (title, text) = when {
            attempt > 0 -> res.getString(R.string.not_confirmed_yet) to
                (listOf(res.getString(R.string.reminder_n_of_n, attempt, totalAttempts)) + lines + res.getString(R.string.tap_taken_when_you_have_taken_it))
                    .joinToString("\n")
            medications.size == 1 -> {
                val m = medications.first()
                val amount = Amounts.text(res, m.effectiveUnitsPerDose, m.unit)
                m.name to if (m.dosageText.isBlank()) res.getString(R.string.time_to_take_x, amount) else res.getString(R.string.time_to_take_x_x, amount, m.dosageText)
            }
            else -> res.getQuantityString(R.plurals.n_medications, medications.size, medications.size) to lines.joinToString("\n")
        }
        val builder = NotificationCompat.Builder(context, currentDoseChannel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp())
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .addExtras(android.os.Bundle().apply { putString(EXTRA_GROUP, group.encode()) })
            .addAction(0, res.getString(R.string.taken), action(ACTION_TAKEN, tag, group))
            .addAction(0, res.getString(R.string.snooze_n_min, snoozeMinutes), action(ACTION_SNOOZE, tag, group))
            .addAction(0, res.getString(R.string.skip), action(ACTION_SKIP, tag, group))
        post(tag, builder)
    }

    fun postMeal(meal: MealReady) {
        if (!allowed) return
        val builder = NotificationCompat.Builder(context, currentMealChannel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.you_can_eat_now))
            .setContentText(res.getString(R.string.n_minutes_have_passed_since_x, meal.waitMinutes, meal.name))
            .setContentIntent(openApp())
            .setAutoCancel(true)
        post("meal|${meal.medicationId}|${meal.scheduledAt.toEpochMilli()}", builder)
    }

    fun postLowStock(medication: Medication) {
        if (!allowed) return
        val text = if (medication.isOutOfStock) {
            res.getString(R.string.you_are_out_get_more_as_soon_as_you_can)
        } else {
            res.getString(R.string.only_x_left_time_to_get_more, Amounts.text(res, medication.stockCount, medication.unit))
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_SUPPLY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.running_low_x, medication.name))
            .setContentText(text)
            .setContentIntent(openApp())
            .setAutoCancel(true)
        post("lowstock|${medication.id}", builder)
    }

    fun postRunOut(medication: Medication, runOut: Instant, zone: ZoneId) {
        if (!allowed) return
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(res.configuration.locales[0]).format(runOut.atZone(zone))
        val builder = NotificationCompat.Builder(context, CHANNEL_SUPPLY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.supply_of_x_is_running_out, medication.name))
            .setContentText(res.getString(R.string.runs_out_on_x_time_to_get_more, date))
            .setContentIntent(openApp())
            .setAutoCancel(true)
        post("runout|${medication.id}", builder)
    }

    /** "Taken" from a notification right after another dose: not recorded, and why. */
    fun postNotRecorded(medication: Medication, takenAt: Instant, zone: ZoneId) {
        if (!allowed) return
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(res.configuration.locales[0]).format(takenAt.atZone(zone))
        val text = res.getString(R.string.it_was_already_taken_at_x_if_you_really_took_anoth, time)
        val builder = NotificationCompat.Builder(context, currentDoseChannel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(res.getString(R.string.not_recorded_x, medication.name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .setAutoCancel(true)
        post("notrecorded|${medication.id}", builder)
    }

    fun cancel(tag: String) = manager.cancel(tag, ID)

    /** Dose and snooze notifications on screen now, with the doses they stand for. */
    fun shownDoseGroups(): List<Pair<String, DoseGroup>> {
        val system = context.getSystemService(NotificationManager::class.java)
        return system.activeNotifications.mapNotNull { shown ->
            val tag = shown.tag ?: return@mapNotNull null
            if (!tag.startsWith("dose|") && !tag.startsWith("snooze|")) return@mapNotNull null
            DoseGroup.decode(shown.notification.extras.getString(EXTRA_GROUP))?.let { tag to it }
        }
    }

    private fun post(tag: String, builder: NotificationCompat.Builder) {
        try {
            manager.notify(tag, ID, builder.build())
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post: nothing to show then.
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(action: String, tag: String, group: DoseGroup): PendingIntent = PendingIntent.getBroadcast(
        context,
        (tag + action).hashCode(),
        Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_TAG, tag)
            .putExtra(EXTRA_GROUP, group.encode()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_DOSES = "doses"
        const val CHANNEL_MEALS = "meals"
        const val CHANNEL_SUPPLY = "supply"
        const val ACTION_TAKEN = "app.vzato.TAKEN"
        const val ACTION_SNOOZE = "app.vzato.SNOOZE"
        const val ACTION_SKIP = "app.vzato.SKIP"
        const val EXTRA_TAG = "tag"
        const val EXTRA_GROUP = "group"
        private const val ID = 1
    }
}
