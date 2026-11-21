// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vzato.R
import app.vzato.ui.Palette
import app.vzato.ui.locale

private class Topic(val title: Int, val steps: List<Int>)

/** The how-tos of the iOS app that apply on Android, in numbered steps, offline. */
private val topics = listOf(
    Topic(R.string.add_a_medication, listOf(
        R.string.open_medications_and_tap_add_medication, R.string.write_the_name_and_how_much_you_take_at_once,
        R.string.under_schedule_set_when_you_take_it_add_time_adds, R.string.if_vzato_should_count_your_tablets_turn_on_track_s,
        R.string.tap_save,
    )),
    Topic(R.string.when_a_reminder_comes, listOf(
        R.string.tap_taken_in_the_reminder_or_in_the_app_with_sever, R.string.no_answer_vzato_reminds_you_again_twice_every_10_m,
        R.string.not_now_tap_snooze_the_reminder_comes_back_after_t, R.string.not_taking_this_dose_tap_skip,
        R.string.tapped_by_mistake_tap_undo_right_away_later_touch,
    )),
    Topic(R.string.why_there_is_no_taken_button, listOf(
        R.string.the_taken_button_appears_from_30_minutes_before_th, R.string.if_you_have_to_take_a_later_dose_now_touch_and_hol,
    )),
    Topic(R.string.supply, listOf(
        R.string.every_dose_you_take_is_subtracted_from_the_supply, R.string.medications_shows_how_much_is_left_and_by_when_to,
        R.string.help_new_package, R.string.not_sure_the_number_is_right_count_your_tablets_ta,
    )),
    Topic(R.string.once_a_month_or_every_other_day, listOf(
        R.string.in_the_medication_form_under_schedule_choose_repea, R.string.pick_every_few_days_weeks_or_months_and_set_the_nu,
        R.string.the_start_date_decides_the_days_below_you_see_the,
    )),
    Topic(R.string.history_and_your_doctor, listOf(
        R.string.history_shows_every_day_in_a_calendar_green_is_tak, R.string.tap_a_day_to_see_it_or_correct_it,
    )),
    Topic(R.string.i_did_not_get_a_reminder, listOf(
        R.string.if_today_shows_reminders_are_off_tap_allow_notific, R.string.help_battery, R.string.help_alarms,
        R.string.help_volume, R.string.in_settings_sounds_you_can_choose_a_different_soun,
    )),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    val topic = open?.let(topics::getOrNull)
    BackHandler { if (topic != null) open = null else onClose() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(topic?.title ?: R.string.help)) },
                navigationIcon = {
                    IconButton(onClick = { if (topic != null) open = null else onClose() }) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (topic != null) {
                itemsIndexed(topic.steps) { index, step ->
                    Row(Modifier.padding(vertical = 10.dp)) {
                        Text("${index + 1}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Palette.accent)
                        androidx.compose.foundation.layout.Spacer(Modifier.width(14.dp))
                        Text(stringResource(step), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                itemsIndexed(topics) { index, item ->
                    Text(
                        stringResource(item.title),
                        Modifier.fillMaxWidth().clickable { open = index }.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    HorizontalDivider()
                }
                item {
                    val language = locale().language.takeIf { it in listOf("cs", "sk", "hu") } ?: "en"
                    Text(
                        stringResource(R.string.guide_with_pictures_on_vzato_app),
                        Modifier.fillMaxWidth().clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://vzato.app/$language/help/")))
                            }
                        }.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.vzato_is_not_a_medical_device_ask_your_doctor_or_p),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
