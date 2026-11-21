// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui.today

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.vzato.R
import app.vzato.reminders.ReminderStatus
import app.vzato.ui.Palette

/** "Reminders are off" and "Reminders may come late", with the button that fixes it. */
@Composable
fun ReminderBanners(status: ReminderStatus, onChanged: () -> Unit) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        asked = true
        onChanged()
        if (!granted) openNotificationSettings(context)
    }
    if (!status.notificationsAllowed) {
        Warning(
            title = stringResource(R.string.reminders_are_off),
            text = stringResource(R.string.without_them_you_will_not_hear_a_reminder_and_noth),
            button = stringResource(if (asked) R.string.open_settings else R.string.allow_notifications),
        ) {
            if (Build.VERSION.SDK_INT >= 33 && !asked) request.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(context)
        }
    } else if (!status.exactAlarmsAllowed && Build.VERSION.SDK_INT >= 31) {
        Warning(
            title = stringResource(R.string.reminders_may_come_late),
            text = stringResource(R.string.allow_alarms_and_reminders),
            button = stringResource(R.string.open_settings),
            color = Palette.overdue,
        ) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
private fun Warning(title: String, text: String, button: String, color: Color = Palette.missed, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)) { Text(button) }
    }
}
