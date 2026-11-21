// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui.history

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.vzato.R
import app.vzato.core.CsvExport
import app.vzato.core.DaySummary
import app.vzato.core.DoseScheduler
import app.vzato.core.DoseSummary
import app.vzato.core.day
import app.vzato.data.Graph
import app.vzato.ui.AppViewModel
import app.vzato.ui.Palette
import app.vzato.ui.SlotRef
import app.vzato.ui.locale
import app.vzato.ui.today.DoseRow
import app.vzato.ui.weekOrder
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** History: a month calendar with the state of every day, the chosen day's doses, the month's totals. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel, graph: Graph, now: Instant) {
    val zone = vm.zone
    val grace = vm.settings.grace
    val locale = locale()
    val context = LocalContext.current
    var month by rememberSaveable { mutableStateOf(YearMonth.from(now.day(zone)).toString()) }
    var selected by rememberSaveable { mutableStateOf(now.day(zone).toString()) }
    val yearMonth = YearMonth.parse(month)
    val selectedDay = LocalDate.parse(selected)
    val medications = graph.medications
    val summaries = DoseSummary.byDay(medications, yearMonth, now, grace, zone)
    val totals = DoseSummary.total(summaries.values)
    val daySlots = DoseScheduler.slots(medications, selectedDay, zone)
    val fileName = stringResource(R.string.vzato_history_x_csv, now.day(zone).toString())
    val exportTitle = stringResource(R.string.all_doses_as_a_table_csv)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history)) },
                actions = {
                    if (medications.isNotEmpty()) {
                        TextButton(onClick = { shareCsv(context, graph, vm, fileName, exportTitle) }) {
                            Text(stringResource(R.string.export))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                MonthCalendar(
                    month = yearMonth,
                    selected = selectedDay,
                    today = now.day(zone),
                    summaries = summaries,
                    onMonth = { month = it.toString() },
                    onSelect = { selected = it.toString() },
                )
            }
            item {
                Text(
                    selectedDay.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(locale))
                        .replaceFirstChar { it.titlecase(locale) },
                    Modifier.padding(top = 20.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (daySlots.isEmpty()) {
                item { Text(stringResource(R.string.no_doses_on_this_day), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(daySlots, key = { it.id }) { slot ->
                DoseRow(
                    slot = slot,
                    now = now,
                    grace = grace,
                    profileName = if (graph.hasSeveralProfiles) graph.profile(slot.medication.profileId)?.name else null,
                    // A dose confirmed later in History counts as taken at its time.
                    onTaken = { vm.takeAt(SlotRef.of(slot), slot.scheduledAt) },
                    onSkip = { vm.skip(SlotRef.of(slot)) },
                    onReset = { vm.reset(listOf(SlotRef.of(slot))) },
                    showsTakeButton = false,
                )
                HorizontalDivider()
            }
            item { MonthTotals(totals, locale) }
        }
    }
}

@Composable
private fun MonthTotals(totals: DaySummary, locale: java.util.Locale) {
    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.this_month).uppercase(locale),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Line(stringResource(R.string.taken), totals.taken.toString())
        Line(stringResource(R.string.missed), totals.missed.toString())
        Line(stringResource(R.string.skipped), totals.skipped.toString())
        totals.adherence?.let { Line(stringResource(R.string.adherence), NumberFormat.getPercentInstance(locale).format(it)) }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    summaries: Map<LocalDate, DaySummary>,
    onMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val locale = locale()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val leading = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.previous_month))
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) {
                Icon(painterResource(R.drawable.ic_forward), stringResource(R.string.next_month))
            }
        }
        Row {
            weekOrder(locale).forEach { apple ->
                val day = java.time.DayOfWeek.of(if (apple == 1) 7 else apple - 1)
                Text(
                    day.getDisplayName(TextStyle.NARROW_STANDALONE, locale), Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(48.dp).padding(2.dp)) {
                        if (day != null) DayCell(day, day == selected, day == today, summaries[day]) { onSelect(day) }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, isSelected: Boolean, isToday: Boolean, summary: DaySummary?, onClick: () -> Unit) {
    val dot: Color? = when {
        summary == null || summary.total == 0 -> null
        summary.missed > 0 -> Palette.missed
        summary.pending > 0 -> MaterialTheme.colorScheme.outlineVariant
        summary.taken > 0 -> Palette.taken
        else -> Palette.skipped
    }
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Palette.accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(BorderStroke(1.dp, if (isToday) Palette.accent else Color.Transparent), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(day.dayOfMonth.toString(), fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot ?: Color.Transparent))
    }
}

/** Writes the table to a `.csv` file (with a BOM so Excel reads the accents) and opens sharing. */
private fun shareCsv(context: Context, graph: Graph, vm: AppViewModel, fileName: String, title: String) {
    val now = Instant.now()
    val missed = CsvExport.missedSlots(graph.medications, now, vm.settings.grace, vm.zone)
    val events = graph.medications.flatMap { it.events }
    val text = CsvExport.make(events, missed) { id -> graph.profile(id)?.name ?: "" }
    val folder = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(folder, fileName)
    file.writeText("\uFEFF" + text)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, fileName)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, title))
}
