// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import app.vzato.R
import app.vzato.core.ActivityTexts
import app.vzato.core.DoseStatus
import app.vzato.core.Medication
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Activity log details in the app's language, as written at the time of the action (as on iOS). */
class AndroidActivityTexts(private val context: Context) : ActivityTexts {
    private val resources get() = context.resources

    override fun recordedLater(takenAt: Instant): String {
        val text = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(resources.configuration.locales[0])
            .format(takenAt.atZone(ZoneId.systemDefault()))
        return resources.getString(R.string.recorded_later_taken_at_x, text)
    }

    override fun previously(status: DoseStatus): String =
        resources.getString(R.string.previously_x, resources.getString(statusLabel(status)))

    override fun stockCounted(medication: Medication, count: Double, previous: Double?): String {
        val now = Amounts.text(resources, count, medication.unit)
        return previous?.let { resources.getString(R.string.x_was_x, now, Amounts.text(resources, it, medication.unit)) } ?: now
    }

    override fun supplyCheck(medication: Medication, counted: Double, expected: Double, backfilled: Int): String {
        val text = resources.getString(
            R.string.counted_x_records_expected_x,
            Amounts.text(resources, counted, medication.unit),
            Amounts.text(resources, expected, medication.unit),
        )
        if (backfilled <= 0) return text
        return text + " " + resources.getString(R.string.recorded_n_missing_doses_as_taken, backfilled)
    }

    companion object {
        fun statusLabel(status: DoseStatus) = when (status) {
            DoseStatus.PENDING -> R.string.pending
            DoseStatus.TAKEN -> R.string.taken
            DoseStatus.SKIPPED -> R.string.skipped
            DoseStatus.MISSED -> R.string.missed
        }

        /** "Pixel 9a", or the name the person gave the phone. */
        fun deviceName(context: Context): String =
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
                ?: Build.MODEL
    }
}
