// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui.medications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vzato.R
import app.vzato.core.Medication
import app.vzato.core.StockService
import app.vzato.data.Graph
import app.vzato.ui.AppViewModel
import app.vzato.ui.Palette
import app.vzato.ui.amountText
import app.vzato.ui.dateText
import app.vzato.ui.doseAmountText
import app.vzato.ui.locale
import app.vzato.ui.minutesText
import app.vzato.ui.repeatText
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationsScreen(vm: AppViewModel, graph: Graph, now: Instant, onOpen: (UUID) -> Unit, onAdd: () -> Unit) {
    val active = graph.activeMedications
    val archived = graph.archivedMedications
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.medications)) }) },
        floatingActionButton = {
            if (active.isNotEmpty() || archived.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.add_medication)) },
                )
            }
        },
    ) { padding ->
        if (active.isEmpty() && archived.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.no_medications), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.add_your_first_medication_with_its_dose_and_times),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onAdd, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.add_medication), style = MaterialTheme.typography.titleMedium)
                }
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { SectionTitle(stringResource(R.string.my_medications)) }
            items(active, key = { it.id }) { medication ->
                MedicationRow(vm, medication, now, onClick = { onOpen(medication.id) })
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
            if (archived.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.archived_medications)) }
                items(archived, key = { it.id }) { medication ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(medication.name, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { vm.restore(medication.id) }) { Text(stringResource(R.string.restore_medication)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(locale()),
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MedicationRow(vm: AppViewModel, medication: Medication, now: Instant, onClick: () -> Unit) {
    val locale = locale()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(Palette.fromHex(medication.colorHex)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(medication.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(doseAmountText(medication), medication.dosageText.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            medication.schedules.firstOrNull()?.let { schedule ->
                Text(
                    repeatText(schedule) + " · " + schedule.minutesOfDay.sorted().joinToString(", ") { minutesText(it, locale) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (medication.tracksStock) {
                val stock = amountText(medication.stockCount, medication.unit)
                if (medication.isLowStock) {
                    Text(stringResource(R.string.running_low_x_left, stock), style = MaterialTheme.typography.bodySmall, color = Palette.overdue)
                } else {
                    val reorder = StockService.reorderDate(medication, now, vm.zone, vm.settings.runOutWarningDays)
                    val text = listOfNotNull(
                        stringResource(R.string.supply_x, stock),
                        reorder?.let { stringResource(R.string.get_more_by_x, dateText(it, locale)) },
                    ).joinToString(" · ")
                    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
