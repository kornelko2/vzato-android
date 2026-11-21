// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui.medications

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.vzato.R
import app.vzato.core.AmountParser
import app.vzato.core.DoseUnit
import app.vzato.core.MealRelation
import app.vzato.core.RepeatKind
import app.vzato.core.ScheduleRules
import app.vzato.ui.MedicationDraft
import app.vzato.ui.Palette
import app.vzato.ui.amountText
import app.vzato.ui.dateText
import app.vzato.ui.locale
import app.vzato.ui.mealLabel
import app.vzato.ui.minutesText
import app.vzato.ui.unitLabel
import app.vzato.ui.weekOrder
import app.vzato.ui.weekdayShort
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** New or edited medication: name, amount, plan, meals, color, supply. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedicationForm(
    initial: MedicationDraft,
    onSave: (MedicationDraft) -> Unit,
    onClose: () -> Unit,
    onArchive: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onCheckSupply: (() -> Unit)? = null,
) {
    var draft by remember { mutableStateOf(initial) }
    var stockText by rememberSaveable { mutableStateOf(initial.stockCount?.let(AmountParser::format) ?: "") }
    var thresholdText by rememberSaveable { mutableStateOf(if (initial.lowStockThreshold > 0) AmountParser.format(initial.lowStockThreshold) else "") }
    var editingTime by remember { mutableStateOf<Int?>(null) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val locale = locale()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (initial.id == null) R.string.new_medication else R.string.edit_medication)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cancel)) }
                },
                actions = {
                    TextButton(enabled = draft.isValid, onClick = {
                        onSave(
                            draft.copy(
                                stockCount = if (draft.tracksStock) AmountParser.parse(stockText) ?: draft.stockCount ?: 0.0 else null,
                                lowStockThreshold = AmountParser.parse(thresholdText) ?: 0.0,
                            ),
                        )
                    }) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(stringResource(R.string.medication))
            OutlinedTextField(
                value = draft.name, onValueChange = { draft = draft.copy(name = it) },
                label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.dosageText, onValueChange = { draft = draft.copy(dosageText = it) },
                label = { Text(stringResource(R.string.dose_e_g_400_mg)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Section(stringResource(R.string.dose_and_supply))
            Choice(
                label = stringResource(R.string.unit),
                options = DoseUnit.entries,
                selected = draft.unit,
                text = { stringResource(unitLabel(it)) },
            ) { unit ->
                val amount = if (unit.allowsFractions) draft.unitsPerDose else maxOf(1.0, kotlin.math.round(draft.unitsPerDose))
                draft = draft.copy(unit = unit, unitsPerDose = amount)
            }
            Stepper(
                label = stringResource(R.string.amount_per_dose),
                value = amountText(draft.unitsPerDose, draft.unit),
                canDecrease = draft.unitsPerDose - draft.unit.step >= draft.unit.step - 0.0001,
                canIncrease = draft.unitsPerDose + draft.unit.step <= draft.unit.maximumPerDose + 0.0001,
                onDecrease = { draft = draft.copy(unitsPerDose = draft.unitsPerDose - draft.unit.step) },
                onIncrease = { draft = draft.copy(unitsPerDose = draft.unitsPerDose + draft.unit.step) },
            )
            SwitchRow(stringResource(R.string.track_supply), draft.tracksStock) { draft = draft.copy(tracksStock = it) }
            if (onCheckSupply != null && initial.tracksStock) {
                OutlinedButton(onClick = onCheckSupply, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.count_and_check_supply)) }
            }
            if (draft.tracksStock) {
                OutlinedTextField(
                    value = stockText, onValueChange = { stockText = it },
                    label = { Text(stringResource(R.string.amount_at_home)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = thresholdText, onValueChange = { thresholdText = it },
                    label = { Text(stringResource(R.string.warn_when_below)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
            }

            Section(stringResource(R.string.schedule))
            Text(stringResource(R.string.times), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.times.sorted().forEach { minutes ->
                    AssistChip(
                        onClick = { editingTime = minutes },
                        label = { Text(minutesText(minutes, locale), style = MaterialTheme.typography.titleMedium) },
                        trailingIcon = if (draft.times.size > 1) {
                            {
                                Icon(
                                    painterResource(R.drawable.ic_close), stringResource(R.string.delete),
                                    Modifier.size(18.dp).clickable { draft = draft.copy(times = draft.times - minutes) },
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
                AssistChip(
                    onClick = { draft = draft.copy(times = draft.times + ScheduleRules.nextSuggestedMinutes(draft.times)) },
                    label = { Text(stringResource(R.string.add_time)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp)) },
                )
            }
            Choice(
                label = stringResource(R.string.repeat),
                options = RepeatKind.entries,
                selected = draft.repeatKind,
                text = {
                    stringResource(
                        when (it) {
                            RepeatKind.WEEKDAYS -> R.string.on_chosen_days
                            RepeatKind.DAYS -> R.string.every_few_days
                            RepeatKind.WEEKS -> R.string.every_few_weeks
                            RepeatKind.MONTHS -> R.string.every_few_months
                        },
                    )
                },
            ) { draft = draft.copy(repeatKind = it) }
            if (draft.repeatKind == RepeatKind.WEEKDAYS) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekOrder(locale).forEach { day ->
                        FilterChip(
                            selected = day in draft.weekdays,
                            onClick = {
                                draft = draft.copy(weekdays = if (day in draft.weekdays) draft.weekdays - day else draft.weekdays + day)
                            },
                            label = { Text(weekdayShort(day, locale)) },
                        )
                    }
                }
            } else {
                val plural = when (draft.repeatKind) {
                    RepeatKind.DAYS -> R.plurals.every_n_days
                    RepeatKind.WEEKS -> R.plurals.every_n_weeks
                    else -> R.plurals.every_n_months
                }
                Stepper(
                    label = stringResource(R.string.repeat),
                    value = pluralStringResource(plural, draft.interval, draft.interval),
                    canDecrease = draft.interval > 1,
                    canIncrease = draft.interval < 365,
                    onDecrease = { draft = draft.copy(interval = draft.interval - 1) },
                    onIncrease = { draft = draft.copy(interval = draft.interval + 1) },
                )
                Text(
                    stringResource(R.string.counted_from_the_start_date_if_it_was_already_give),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ValueRow(stringResource(R.string.start), dateText(draft.startDate, locale)) { pickingStart = true }
            SwitchRow(stringResource(R.string.end_date), draft.endDate != null) {
                draft = draft.copy(endDate = if (it) maxOf(draft.startDate, LocalDate.now()).plusDays(7) else null)
            }
            draft.endDate?.let { end -> ValueRow(stringResource(R.string.end), dateText(end, locale)) { pickingEnd = true } }
            Choice(
                label = stringResource(R.string.meals),
                options = MealRelation.entries,
                selected = draft.mealRelation,
                text = { stringResource(mealLabel(it)) },
            ) { draft = draft.copy(mealRelation = it) }
            if (draft.mealRelation == MealRelation.BEFORE) {
                Stepper(
                    label = stringResource(R.string.eat_after),
                    value = stringResource(R.string.n_min, draft.mealWaitMinutes),
                    canDecrease = draft.mealWaitMinutes > 15,
                    canIncrease = draft.mealWaitMinutes < 120,
                    onDecrease = { draft = draft.copy(mealWaitMinutes = draft.mealWaitMinutes - 15) },
                    onIncrease = { draft = draft.copy(mealWaitMinutes = draft.mealWaitMinutes + 15) },
                )
            }

            Section(stringResource(R.string.color))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Palette.medicationHexes.forEach { hex ->
                    val selected = draft.colorHex.equals(hex, ignoreCase = true)
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Palette.fromHex(hex))
                            .border(BorderStroke(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                            .clickable { draft = draft.copy(colorHex = hex) },
                    )
                }
            }
            OutlinedTextField(
                value = draft.notes, onValueChange = { draft = draft.copy(notes = it) },
                label = { Text(stringResource(R.string.notes)) }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            )

            if (onArchive != null || onDelete != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.archiving_keeps_the_history_deleting_removes_the_m),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                onArchive?.let { OutlinedButton(onClick = it, Modifier.fillMaxWidth()) { Text(stringResource(R.string.archive_medication)) } }
                onDelete?.let {
                    OutlinedButton(onClick = { confirmingDelete = true }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.delete_medication), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.heightIn(min = 32.dp))
        }
    }

    editingTime?.let { minutes ->
        TimeDialog(minutes, onDismiss = { editingTime = null }) { new ->
            editingTime = null
            draft = draft.copy(times = (draft.times - minutes + new).distinct())
        }
    }
    if (pickingStart) DateDialog(draft.startDate, onDismiss = { pickingStart = false }) {
        pickingStart = false
        draft = draft.copy(startDate = it, endDate = draft.endDate?.let { end -> maxOf(end, it) })
    }
    if (pickingEnd) DateDialog(draft.endDate ?: draft.startDate, onDismiss = { pickingEnd = false }) {
        pickingEnd = false
        draft = draft.copy(endDate = maxOf(it, draft.startDate))
    }
    if (confirmingDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_this_medication_and_its_history)) },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(locale()),
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onDecrease, enabled = canDecrease) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onIncrease, enabled = canIncrease) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choice(label: String, options: List<T>, selected: T, text: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = text(selected), onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(text(option)) }, onClick = { expanded = false; onSelect(option) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(minutes / 60, minutes % 60, DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(date: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    // The picker works in UTC midnight milliseconds.
    val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state) }
}
