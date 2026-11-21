// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui.onboarding

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vzato.R
import app.vzato.core.DoseUnit
import app.vzato.ui.MedicationDraft
import app.vzato.ui.Palette
import app.vzato.ui.amountText
import app.vzato.ui.locale
import app.vzato.ui.minutesText
import app.vzato.ui.unitLabel

private enum class Step { WELCOME, NOTIFICATIONS, NAME, AMOUNT, TIMES, DONE }

private data class TimeChoice(val label: Int, val minutes: Int, val on: Boolean)

/**
 * First start, for people who do not use phones much: welcome, then the first medication in three
 * steps (name, how much, when) and a summary, with the notification permission (and why) in
 * between. Every step can be skipped.
 */
@Composable
fun Onboarding(onSave: (MedicationDraft) -> Unit, onFinish: () -> Unit) {
    var step by remember { mutableStateOf(Step.WELCOME) }
    var draft by remember { mutableStateOf(MedicationDraft(times = emptyList())) }
    var choices by remember {
        mutableStateOf(
            listOf(
                TimeChoice(R.string.morning, 8 * 60, true),
                TimeChoice(R.string.noon, 12 * 60, false),
                TimeChoice(R.string.evening, 18 * 60, false),
                TimeChoice(R.string.night, 22 * 60, false),
            ),
        )
    }
    var editing by remember { mutableStateOf<Int?>(null) }
    val locale = locale()
    val times = choices.filter { it.on }.map { it.minutes }.sorted()

    BackHandler(enabled = step != Step.WELCOME && step != Step.DONE) { step = Step.entries[step.ordinal - 1] }

    val context = LocalContext.current
    val engine = app.vzato.VzatoApp.of(context).reminders
    var notificationsAllowed by remember { mutableStateOf(engine.notifications.allowed) }
    var asked by remember { mutableStateOf(false) }
    val request = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        asked = true
        notificationsAllowed = granted || engine.notifications.allowed
    }

    val canContinue = when (step) {
        Step.NAME -> draft.name.isNotBlank()
        Step.TIMES -> times.isNotEmpty()
        else -> true
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step in listOf(Step.NAME, Step.AMOUNT, Step.TIMES)) {
                TextButton(onClick = { step = Step.entries[step.ordinal - 1] }) { Text(stringResource(R.string.back)) }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.step_n_of_n, step.ordinal - 1, 3),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (step != Step.DONE) {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.skip)) }
            }
        }
        Column(
            Modifier.weight(1f).widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (step) {
                Step.WELCOME -> {
                    Image(
                        painterResource(R.drawable.ic_launcher_foreground), null,
                        Modifier.padding(top = 24.dp).size(110.dp).clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF3F8A5C)).align(Alignment.CenterHorizontally),
                    )
                    Title(stringResource(R.string.vzato_reminds_you_of_your_medications))
                    Body(stringResource(R.string.when_it_is_time_you_only_tap_taken_vzato_remembers))
                    Body(stringResource(R.string.let_s_set_it_up_together_it_takes_about_a_minute), secondary = true)
                }
                Step.NOTIFICATIONS -> {
                    Text("🔔", Modifier.padding(top = 24.dp).align(Alignment.CenterHorizontally), fontSize = 64.sp)
                    Title(stringResource(R.string.allow_notifications))
                    Body(stringResource(R.string.without_them_you_will_not_hear_a_reminder_and_noth))
                    when {
                        notificationsAllowed -> Text(
                            "✓ " + stringResource(R.string.notifications_are_allowed),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Palette.taken,
                        )
                        asked -> Body(stringResource(R.string.notifications_are_turned_off_for_vzato_turn_them_o))
                        else -> Body(stringResource(R.string.on_the_next_screen_the_system_asks_tap_allow), secondary = true)
                    }
                }
                Step.NAME -> {
                    Title(stringResource(R.string.what_is_the_medication_called))
                    OutlinedTextField(
                        draft.name, { draft = draft.copy(name = it) }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.name)) },
                        placeholder = { Text(stringResource(R.string.e_g_ibuprofen)) },
                        textStyle = MaterialTheme.typography.headlineSmall, singleLine = true,
                    )
                    Body(stringResource(R.string.strength_if_it_is_on_the_package_optional))
                    OutlinedTextField(
                        draft.dosageText, { draft = draft.copy(dosageText = it) }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.strength)) },
                        placeholder = { Text(stringResource(R.string.e_g_400_mg)) }, singleLine = true,
                    )
                }
                Step.AMOUNT -> {
                    Title(stringResource(R.string.how_much_do_you_take_at_once))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(DoseUnit.TABLET, DoseUnit.CAPSULE, DoseUnit.DROP, DoseUnit.MILLILITER).forEach { unit ->
                            val selected = draft.unit == unit
                            OutlinedButton(
                                onClick = { draft = draft.copy(unit = unit, unitsPerDose = maxOf(1.0, kotlin.math.round(draft.unitsPerDose))) },
                                colors = if (selected) ButtonDefaults.outlinedButtonColors(containerColor = Palette.taken.copy(alpha = 0.15f)) else ButtonDefaults.outlinedButtonColors(),
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                            ) { Text(stringResource(unitLabel(unit)), maxLines = 1, fontSize = 13.sp) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Big("−", enabled = draft.unitsPerDose > 1) { draft = draft.copy(unitsPerDose = draft.unitsPerDose - 1) }
                        Text(
                            amountText(draft.unitsPerDose, draft.unit),
                            Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 32.sp, fontWeight = FontWeight.Black,
                        )
                        Big("+", enabled = draft.unitsPerDose < draft.unit.maximumPerDose) { draft = draft.copy(unitsPerDose = draft.unitsPerDose + 1) }
                    }
                    Body(stringResource(R.string.a_quarter_tablet_or_a_different_amount_can_be_set), secondary = true)
                }
                Step.TIMES -> {
                    Title(stringResource(R.string.when_do_you_take_it))
                    Body(stringResource(R.string.every_day_turn_on_one_or_more_times_tap_a_time_to), secondary = true)
                    choices.forEachIndexed { index, choice ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(choice.label), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            if (choice.on) {
                                Text(
                                    minutesText(choice.minutes, locale),
                                    Modifier.clickable { editing = index }.padding(horizontal = 12.dp),
                                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Switch(choice.on, { on -> choices = choices.mapIndexed { i, c -> if (i == index) c.copy(on = on) else c } })
                        }
                    }
                }
                Step.DONE -> {
                    Text("✓", Modifier.padding(top = 24.dp).align(Alignment.CenterHorizontally), fontSize = 72.sp, color = Palette.taken)
                    Title(stringResource(R.string.all_set))
                    Text("${amountText(draft.unitsPerDose, draft.unit)} ${draft.name.trim()}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Body(stringResource(R.string.every_day_at_x, times.joinToString(", ") { minutesText(it, locale) }))
                    Body(stringResource(R.string.when_it_is_time_vzato_reminds_you_then_tap_taken))
                    Body(stringResource(R.string.supply_photos_and_meals_can_be_added_in_medication), secondary = true)
                }
            }
        }
        Button(
            onClick = {
                when (step) {
                    Step.NOTIFICATIONS -> when {
                        notificationsAllowed -> step = Step.NAME
                        asked || android.os.Build.VERSION.SDK_INT < 33 -> {
                            app.vzato.ui.today.openNotificationSettings(context)
                            step = Step.NAME
                        }
                        else -> request.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    Step.TIMES -> {
                        onSave(draft.copy(times = times))
                        step = Step.DONE
                    }
                    Step.DONE -> onFinish()
                    else -> step = Step.entries[step.ordinal + 1]
                }
            },
            enabled = canContinue,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.taken, contentColor = Color.White),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).heightIn(min = 56.dp),
        ) {
            Text(
                stringResource(
                    when (step) {
                        Step.WELCOME -> R.string.let_s_start
                        Step.NOTIFICATIONS -> when {
                            notificationsAllowed -> R.string.s_continue
                            asked -> R.string.open_settings
                            else -> R.string.allow_notifications
                        }
                        Step.TIMES -> R.string.save
                        Step.DONE -> R.string.done
                        else -> R.string.s_continue
                    },
                ),
                fontSize = 20.sp, fontWeight = FontWeight.Black,
            )
        }
    }

    editing?.let { index ->
        OnboardingTimeDialog(choices[index].minutes, onDismiss = { editing = null }) { minutes ->
            editing = null
            choices = choices.mapIndexed { i, c -> if (i == index) c.copy(minutes = minutes) else c }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

@Composable
private fun Body(text: String, secondary: Boolean = false) = Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    color = if (secondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun Big(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(64.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Text(symbol, fontSize = 28.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingTimeDialog(minutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(minutes / 60, minutes % 60, DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
