// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui.settings

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.vzato.R
import app.vzato.VzatoApp
import app.vzato.data.ReminderSound
import app.vzato.ui.AppViewModel
import app.vzato.ui.Palette
import app.vzato.ui.locale
import app.vzato.ui.today.openNotificationSettings
import kotlinx.coroutines.launch

/** Settings: reminders, sounds, supply, appearance, language, the activity log, help and about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onActivityLog: () -> Unit, onHelp: () -> Unit, onIntroduction: () -> Unit) {
    val context = LocalContext.current
    val settings = vm.settings
    val status by vm.reminderStatus.collectAsState()
    // Settings are plain preferences; a version counter redraws after each change.
    var version by remember { mutableIntStateOf(0) }
    fun changed() {
        version++
        vm.settingsChanged()
    }
    val engine = VzatoApp.of(context).reminders
    // Read here so a change redraws the screen.
    val scroll = rememberScrollState()
    key(version) { SettingsContent(vm, status, scroll, ::changed, onActivityLog, onHelp, onIntroduction) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    vm: AppViewModel,
    status: app.vzato.reminders.ReminderStatus,
    scroll: androidx.compose.foundation.ScrollState,
    changed: () -> Unit,
    onActivityLog: () -> Unit,
    onHelp: () -> Unit,
    onIntroduction: () -> Unit,
) {
    val context = LocalContext.current
    val settings = vm.settings
    val engine = VzatoApp.of(context).reminders

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }) }) { padding ->
        // A plain scrolling column, not a lazy list: every row reads the settings, and a change
        // (the version counter) must redraw all of them.
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(16.dp, 8.dp, 16.dp, 32.dp)) {
            run { Section(stringResource(R.string.reminders)) }
            run {
                if (status.notificationsAllowed) {
                    Note("✓ " + stringResource(R.string.notifications_are_allowed), Palette.taken)
                } else {
                    Note(stringResource(R.string.notifications_are_turned_off), Palette.missed)
                    Text(stringResource(R.string.turn_them_on_in_the_system_settings_to_get_reminde), style = MaterialTheme.typography.bodySmall)
                    Action(stringResource(R.string.open_notification_settings)) { openNotificationSettings(context) }
                }
            }
            if (!status.exactAlarmsAllowed && Build.VERSION.SDK_INT >= 31) {
                run {
                    Note(stringResource(R.string.reminders_may_come_late), Palette.overdue)
                    Text(stringResource(R.string.allow_alarms_and_reminders), style = MaterialTheme.typography.bodySmall)
                    Action(stringResource(R.string.open_settings)) {
                        open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                }
            }
            if (!ignoresBatteryOptimizations(context)) {
                run {
                    Note(stringResource(R.string.phone_may_stop_reminders), Palette.overdue)
                    Text(stringResource(R.string.battery_optimization_text), style = MaterialTheme.typography.bodySmall)
                    // The list of apps, not the direct request: Google Play allows that one only to a few kinds of apps.
                    Action(stringResource(R.string.open_settings)) { open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }
            }
            run {
                Stepper(stringResource(R.string.snooze), stringResource(R.string.n_min, settings.snoozeMinutes),
                    settings.snoozeMinutes > 5, settings.snoozeMinutes < 60,
                    { settings.snoozeMinutes -= 5; changed() }, { settings.snoozeMinutes += 5; changed() })
                Stepper(stringResource(R.string.repeat_unconfirmed_reminders),
                    if (settings.repeatCount == 0) stringResource(R.string.off) else "${settings.repeatCount}×",
                    settings.repeatCount > 0, settings.repeatCount < 3,
                    { settings.repeatCount -= 1; changed() }, { settings.repeatCount += 1; changed() })
                Stepper(stringResource(R.string.mark_as_missed_after), stringResource(R.string.n_min, settings.missedAfterMinutes),
                    settings.missedAfterMinutes > 30, settings.missedAfterMinutes < 360,
                    { settings.missedAfterMinutes -= 30; changed() }, { settings.missedAfterMinutes += 30; changed() })
            }
            run {
                Text(stringResource(R.string.do_not_disturb), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.do_not_disturb_text), style = MaterialTheme.typography.bodySmall)
                Action(stringResource(R.string.open_notification_settings)) {
                    open(
                        context,
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, engine.notifications.currentDoseChannel),
                    )
                }
                Action(stringResource(R.string.refresh_reminders)) { vm.settingsChanged() }
            }

            run { Section(stringResource(R.string.sounds)) }
            run {
                Text(stringResource(R.string.different_sounds_tell_you_at_a_glance_whether_it_i), style = MaterialTheme.typography.bodySmall)
                SoundPicker(stringResource(R.string.medication_reminders), ReminderSound.forDoses, settings.doseSound) {
                    settings.doseSound = it; changed(); play(context, engine.notifications.soundUri(it))
                }
                SoundPicker(stringResource(R.string.you_can_eat_now), ReminderSound.forMeals, settings.mealSound) {
                    settings.mealSound = it; changed(); play(context, engine.notifications.soundUri(it))
                }
            }

            run { Section(stringResource(R.string.supply)) }
            run {
                SwitchRow(stringResource(R.string.low_supply_alerts), settings.lowStockNotifications) { settings.lowStockNotifications = it; changed() }
                Stepper(stringResource(R.string.warn_before_running_out),
                    if (settings.runOutWarningDays == 0) stringResource(R.string.off)
                    else pluralStringResource(R.plurals.n_days, settings.runOutWarningDays, settings.runOutWarningDays),
                    settings.runOutWarningDays > 0, settings.runOutWarningDays < 30,
                    { settings.runOutWarningDays -= 1; changed() }, { settings.runOutWarningDays += 1; changed() })
            }

            run { Section(stringResource(R.string.appearance)) }
            run {
                val options = listOf("system" to R.string.system, "light" to R.string.light, "dark" to R.string.dark)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (key, label) ->
                        val selected = settings.appearance == key
                        OutlinedButton(onClick = { settings.appearance = key; changed() }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(label), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Palette.accent else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            run { Section(stringResource(R.string.language)) }
            run {
                Text(stringResource(R.string.vzato_follows_the_language_you_set_for_it_in_the_s), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.app_language_android), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (Build.VERSION.SDK_INT >= 33) {
                    Action(stringResource(R.string.open_settings)) {
                        open(context, Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }
                }
            }

            run { Section(stringResource(R.string.backup)) }
            run { BackupSection(vm) }

            run { Section(stringResource(R.string.activity)) }
            run {
                Link(stringResource(R.string.activity_log), onActivityLog)
                Text(stringResource(R.string.who_recorded_what_and_when_on_which_device), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            run { Section(stringResource(R.string.help)) }
            run {
                Link(stringResource(R.string.help), onHelp)
                Link(stringResource(R.string.show_the_introduction_again), onIntroduction)
            }

            run { Section(stringResource(R.string.free_for_everyone)) }
            run {
                Text(stringResource(R.string.every_feature_is_free_for_everyone_including_the_s))
                Text(stringResource(R.string.i_look_after_vzato_mainly_for_myself_any_comment_o), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Link(stringResource(R.string.write_to_us_info_vzato_app)) { open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:info@vzato.app"))) }
                Link("vzato.app") { browse(context, "https://vzato.app") }
            }

            run { Section(stringResource(R.string.about)) }
            run {
                val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(stringResource(R.string.version), Modifier.weight(1f))
                    Text(versionName)
                }
                Text(stringResource(R.string.vzato_is_not_a_medical_device_and_does_not_replace), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.data_stays_on_this_phone), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val language = locale().language.takeIf { it in listOf("cs", "sk", "hu") } ?: "en"
                Link(stringResource(R.string.privacy_policy)) { browse(context, "https://vzato.app/$language/privacy/") }
                Link(stringResource(R.string.source_code_and_license_mpl_2_0)) { browse(context, "https://github.com/kornelko2/vzato-android") }
            }
        }
    }
}

private fun ignoresBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun browse(context: Context, url: String) = open(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private fun play(context: Context, uri: Uri) {
    runCatching { RingtoneManager.getRingtone(context, uri)?.play() }
}

@Composable
private fun Section(title: String) {
    Column(Modifier.padding(top = 24.dp, bottom = 6.dp)) {
        Text(title.uppercase(locale()), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Note(text: String, color: androidx.compose.ui.graphics.Color) =
    Text(text, color = color, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(label) }
}

@Composable
private fun Link(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun Stepper(label: String, value: String, canDecrease: Boolean, canIncrease: Boolean, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(value, fontWeight = FontWeight.Bold, color = Palette.accent)
        }
        OutlinedButton(onClick = onDecrease, enabled = canDecrease) { Text("−") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onIncrease, enabled = canIncrease) { Text("+") }
    }
}

@Composable
private fun SoundPicker(label: String, options: List<ReminderSound>, selected: ReminderSound, onSelect: (ReminderSound) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp)) {
        Text(label, Modifier.weight(1f))
        Text(stringResource(selected.label), color = Palette.accent, fontWeight = FontWeight.SemiBold)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { sound ->
                DropdownMenuItem(text = { Text(stringResource(sound.label)) }, onClick = { open = false; onSelect(sound) })
            }
        }
    }
}

/** Save everything to a file the person picks, or replace everything from one. */
@Composable
private fun BackupSection(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var restoring by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val saved = stringResource(R.string.backup_saved)
    val restored = stringResource(R.string.backup_restored)
    val failed = stringResource(R.string.backup_failed)
    val fileName = stringResource(R.string.backup_file_name, java.time.LocalDate.now().toString())
    val repository = VzatoApp.of(context).repository
    val save = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            val text = repository.exportBackup(version)
            message = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
                saved
            }.getOrElse { it.localizedMessage }
        }
    }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> restoring = uri }

    Text(stringResource(R.string.backup_text), style = MaterialTheme.typography.bodySmall)
    Link(stringResource(R.string.save_backup)) { save.launch(fileName) }
    Link(stringResource(R.string.restore_backup)) { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
    message?.let { Text(it, color = Palette.accent, fontWeight = FontWeight.SemiBold) }

    restoring?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text(stringResource(R.string.restore_question)) },
            text = { Text(stringResource(R.string.restore_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    restoring = null
                    scope.launch {
                        message = runCatching {
                            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                            }
                            repository.restoreBackup(text)
                            vm.settingsChanged()
                            restored
                        }.getOrElse { failed }
                    }
                }) { Text(stringResource(R.string.restore), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
