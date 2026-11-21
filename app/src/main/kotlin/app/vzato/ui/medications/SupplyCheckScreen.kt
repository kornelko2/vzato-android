// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui.medications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.vzato.R
import app.vzato.core.AmountParser
import app.vzato.core.Medication
import app.vzato.core.ReconciliationReport
import app.vzato.core.ReconciliationService
import app.vzato.ui.Palette
import app.vzato.ui.amountText
import app.vzato.ui.dateText
import app.vzato.ui.locale
import java.time.Instant
import java.time.ZoneId

/** Does the counted supply match the records? Optionally records the doses taken without a record. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplyCheckScreen(medication: Medication, zone: ZoneId, onSave: (Double, Boolean) -> Unit, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var recordMissing by rememberSaveable { mutableStateOf(true) }
    val counted = AmountParser.parse(text)
    val report = ReconciliationService.report(medication, counted, Instant.now(), zone)
    val locale = locale()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.supply_check)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cancel)) } },
                actions = {
                    TextButton(enabled = counted != null, onClick = { counted?.let { onSave(it, recordMissing) } }) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(medication.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.count_what_you_have), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.amount_you_have_at_home)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (!medication.tracksStock) {
                Text(
                    stringResource(R.string.enter_the_number_of_units_to_start_tracking_this_m),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (counted != null) {
                Text(stringResource(R.string.result).uppercase(locale), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                when (val verdict = report.verdict) {
                    ReconciliationReport.Verdict.NotTracking, ReconciliationReport.Verdict.NeedsCount ->
                        Text(stringResource(R.string.supply_tracking_starts_with_this_count))
                    ReconciliationReport.Verdict.Consistent ->
                        Text("✓ " + stringResource(R.string.your_count_matches_the_records), color = Palette.taken, fontWeight = FontWeight.SemiBold)
                    is ReconciliationReport.Verdict.UnrecordedDoses -> {
                        Text(stringResource(R.string.fewer_units_than_the_records_expect), color = Palette.overdue, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(
                                R.string.units_missing_x_that_is_about_n_doses_taken_withou,
                                amountText(report.unitDifference ?: 0.0, medication.unit), verdict.count,
                            ),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (report.unaccounted > 0) {
                            Row(Modifier.fillMaxWidth().clickable { recordMissing = !recordMissing }) {
                                Text(stringResource(R.string.record_the_missing_doses_as_taken), Modifier.weight(1f).padding(top = 12.dp))
                                Switch(recordMissing, { recordMissing = it })
                            }
                        }
                    }
                    is ReconciliationReport.Verdict.OverRecordedDoses -> {
                        Text(stringResource(R.string.more_units_than_the_records_expect), color = Palette.overdue, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(
                                R.string.units_extra_x_about_n_recorded_doses_may_not_have,
                                amountText(-(report.unitDifference ?: 0.0), medication.unit), verdict.count,
                            ),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (medication.tracksStock) {
                Text(
                    stringResource(R.string.since_the_last_count).uppercase(locale),
                    Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                )
                Line(stringResource(R.string.counted_on), dateText(report.since.atZone(zone).toLocalDate(), locale))
                Line(stringResource(R.string.scheduled_doses), report.scheduledDoses.toString())
                Line(stringResource(R.string.recorded_as_taken), report.recordedTaken.toString())
                Line(stringResource(R.string.skipped), report.recordedSkipped.toString())
                Line(stringResource(R.string.without_a_record), report.unaccounted.toString())
                Line(stringResource(R.string.should_be_at_home), amountText(report.expectedStock, medication.unit))
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
