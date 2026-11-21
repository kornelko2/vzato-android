// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.data

import android.content.Context
import androidx.core.content.edit
import app.vzato.core.ReminderPlanner
import java.time.Duration

/** Settings with the same defaults as AppSettings.swift. Kept on this device only. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var snoozeMinutes: Int
        get() = prefs.getInt(SNOOZE, 0).takeIf { it > 0 } ?: DEFAULT_SNOOZE_MINUTES
        set(value) = prefs.edit { putInt(SNOOZE, value) }

    var missedAfterMinutes: Int
        get() = prefs.getInt(MISSED_AFTER, 0).takeIf { it > 0 } ?: DEFAULT_MISSED_AFTER_MINUTES
        set(value) = prefs.edit { putInt(MISSED_AFTER, value) }

    /** Follow-ups of an unconfirmed reminder, 0–3. */
    var repeatCount: Int
        get() = if (prefs.contains(REPEAT)) prefs.getInt(REPEAT, DEFAULT_REPEAT_COUNT).coerceIn(0, 3) else DEFAULT_REPEAT_COUNT
        set(value) = prefs.edit { putInt(REPEAT, value.coerceIn(0, 3)) }

    var runOutWarningDays: Int
        get() = if (prefs.contains(RUN_OUT)) prefs.getInt(RUN_OUT, DEFAULT_RUN_OUT_WARNING_DAYS).coerceIn(0, 30) else DEFAULT_RUN_OUT_WARNING_DAYS
        set(value) = prefs.edit { putInt(RUN_OUT, value.coerceIn(0, 30)) }

    var lowStockNotifications: Boolean
        get() = prefs.getBoolean(LOW_STOCK, true)
        set(value) = prefs.edit { putBoolean(LOW_STOCK, value) }

    var doseSound: ReminderSound
        get() = ReminderSound.fromKey(prefs.getString(DOSE_SOUND, null), ReminderSound.CHIME)
        set(value) = prefs.edit { putString(DOSE_SOUND, value.key) }

    var mealSound: ReminderSound
        get() = ReminderSound.fromKey(prefs.getString(MEAL_SOUND, null), ReminderSound.SOFT)
        set(value) = prefs.edit { putString(MEAL_SOUND, value.key) }

    /** "system", "light" or "dark". */
    var appearance: String
        get() = prefs.getString(APPEARANCE, "system") ?: "system"
        set(value) = prefs.edit { putString(APPEARANCE, value) }

    var onboardingDone: Boolean
        get() = prefs.getBoolean(ONBOARDING, false)
        set(value) = prefs.edit { putBoolean(ONBOARDING, value) }

    val grace: Duration get() = Duration.ofMinutes(missedAfterMinutes.toLong())

    val reminderSettings: ReminderPlanner.Settings
        get() = ReminderPlanner.Settings(snoozeMinutes, repeatCount, missedAfterMinutes)

    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val DEFAULT_MISSED_AFTER_MINUTES = 120
        const val DEFAULT_REPEAT_COUNT = 2
        const val DEFAULT_RUN_OUT_WARNING_DAYS = 7
        private const val SNOOZE = "snoozeMinutes"
        private const val MISSED_AFTER = "missedAfterMinutes"
        private const val REPEAT = "repeatCount"
        private const val RUN_OUT = "runOutWarningDays"
        private const val LOW_STOCK = "lowStockNotifications"
        private const val ONBOARDING = "onboardingDone"
        private const val DOSE_SOUND = "doseSound"
        private const val MEAL_SOUND = "mealSound"
        private const val APPEARANCE = "appearance"
    }
}

/** The sounds of the iOS app (Scripts/make-sounds.py), converted by scripts/convert-sounds.sh. */
enum class ReminderSound(val key: String, val label: Int, val raw: Int?) {
    CHIME("chime", app.vzato.R.string.chime, app.vzato.R.raw.vzato_dose),
    TWOTONE("twotone", app.vzato.R.string.two_tone, app.vzato.R.raw.vzato_dose_twotone),
    GLOCK("glock", app.vzato.R.string.glockenspiel, app.vzato.R.raw.vzato_dose_glock),
    ALERT("alert", app.vzato.R.string.alert, app.vzato.R.raw.vzato_dose_alert),
    DROPLETS("droplets", app.vzato.R.string.droplets, app.vzato.R.raw.vzato_dose_droplets),
    DINGDONG("dingdong", app.vzato.R.string.ding_dong, app.vzato.R.raw.vzato_dose_dingdong),
    SOFT("soft", app.vzato.R.string.soft_melody, app.vzato.R.raw.vzato_meal),
    MARIMBA("marimba", app.vzato.R.string.marimba, app.vzato.R.raw.vzato_meal_marimba),
    HARP("harp", app.vzato.R.string.harp, app.vzato.R.raw.vzato_meal_harp),
    FLUTE("flute", app.vzato.R.string.flute, app.vzato.R.raw.vzato_meal_flute),
    GONG("gong", app.vzato.R.string.gong, app.vzato.R.raw.vzato_meal_gong),
    MEAL_BELL("mealBell", app.vzato.R.string.meal_bell, app.vzato.R.raw.vzato_meal_bell),
    SYSTEM("system", app.vzato.R.string.system_sound, null);

    companion object {
        val forDoses = listOf(CHIME, TWOTONE, GLOCK, ALERT, DROPLETS, DINGDONG, SYSTEM)
        val forMeals = listOf(SOFT, MARIMBA, HARP, FLUTE, GONG, MEAL_BELL, SYSTEM)
        fun fromKey(key: String?, default: ReminderSound) = entries.firstOrNull { it.key == key } ?: default
    }
}
