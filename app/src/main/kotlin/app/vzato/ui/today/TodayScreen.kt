// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vzato.R
import app.vzato.core.DoseGuard
import app.vzato.core.DoseScheduler
import app.vzato.core.DoseSlot
import app.vzato.core.DoseStatus
import app.vzato.core.MealRelation
import app.vzato.core.TodaySnapshot
import app.vzato.core.day
import app.vzato.data.Graph
import app.vzato.ui.AppViewModel
import app.vzato.ui.Palette
import app.vzato.ui.SlotRef
import app.vzato.ui.doseAmountText
import app.vzato.ui.locale
import app.vzato.ui.mealLabel
import app.vzato.ui.timeText
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** How "Taken" works for a dose right now (DoseGuard). */
enum class TakeMode {
    /** The big button. */
    DIRECT,

    /** Due, but the same medication was taken recently: "Taken…" asks first. */
    ASK_FIRST,

    /** More than 30 minutes ahead: only the time shows. */
    NOT_YET,
}

private data class PendingTake(val message: String, val onConfirm: () -> Unit)

/** Today, design direction C: one big card for the dose that needs attention, then the whole day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(vm: AppViewModel, graph: Graph, now: Instant, onAddMedication: () -> Unit) {
    val zone = vm.zone
    val grace = vm.settings.grace
    val locale = locale()
    val medications = graph.activeMedications
    val slots = DoseScheduler.slots(medications, now.day(zone), zone)
    val entry = TodaySnapshot.entry(now, slots, grace)
    val featured = entry.featured?.slot
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingTake?>(null) }
    var showingExtra by remember { mutableStateOf(false) }
    val undoLabel = stringResource(R.string.undo)
    val reminderStatus by vm.reminderStatus.collectAsState()
    val takenLabel = stringResource(R.string.recorded_as_taken)
    val recentTemplate = stringResource(R.string.x_was_already_taken_at_x_take_another_dose_now)
    val earlyTemplate = stringResource(R.string.this_dose_is_planned_for_x_take_it_now_early)

    fun take(refs: List<SlotRef>) = vm.take(refs) { taken ->
        if (taken.isEmpty()) return@take
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(takenLabel, undoLabel, withDismissAction = false, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) vm.reset(taken)
        }
    }

    /** Every "Taken" on Today goes through here: one tap when safe, a question when not. */
    fun requestTake(slot: DoseSlot) {
        when (val verdict = DoseGuard.verdict(slot, Instant.now())) {
            DoseGuard.Verdict.Ok -> take(listOf(SlotRef.of(slot)))
            is DoseGuard.Verdict.RecentlyTaken -> pending = PendingTake(
                recentTemplate.format(slot.medication.name, timeText(verdict.at, locale, zone)),
            ) { take(listOf(SlotRef.of(slot))) }
            is DoseGuard.Verdict.TooEarly -> pending = PendingTake(
                earlyTemplate.format(timeText(verdict.at, locale, zone)),
            ) { take(listOf(SlotRef.of(slot))) }
        }
    }

    fun takeMode(slot: DoseSlot): TakeMode = when (DoseGuard.verdict(slot, now)) {
        DoseGuard.Verdict.Ok -> TakeMode.DIRECT
        is DoseGuard.Verdict.TooEarly -> TakeMode.NOT_YET
        is DoseGuard.Verdict.RecentlyTaken ->
            if (Duration.between(now, slot.scheduledAt) > DoseGuard.earlyWindow) TakeMode.NOT_YET else TakeMode.ASK_FIRST
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today)) },
                actions = {
                    if (slots.isNotEmpty()) {
                        Text(
                            "✓ ${entry.taken} / ${entry.total}",
                            color = Palette.taken,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    if (medications.isNotEmpty()) {
                        IconButton(onClick = { showingExtra = true }) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.log_extra_dose))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (medications.isNotEmpty()) {
                item { ReminderBanners(reminderStatus, onChanged = vm::refreshClock) }
            }
            item {
                when {
                    featured != null -> DoseCard(
                        slot = featured,
                        now = now,
                        grace = grace,
                        showProfile = graph.hasSeveralProfiles,
                        profileName = graph.profile(featured.medication.profileId)?.name,
                        mode = takeMode(featured),
                        onTaken = { requestTake(featured) },
                        onSkip = { vm.skip(SlotRef.of(featured)) },
                        snoozeMinutes = vm.settings.snoozeMinutes,
                        onSnooze = { vm.snooze(listOf(SlotRef.of(featured))) },
                    )
                    slots.isEmpty() -> EmptyToday(noMedications = medications.isEmpty(), onAddMedication = onAddMedication)
                    else -> AllDoneCard()
                }
            }
            mealWait(slots, now, zone)?.let { (readyAt, names) ->
                item { Banner(stringResource(R.string.eat_from_x, timeText(readyAt, locale, zone)) + " · " + names, Palette.accent) }
            }
            val low = medications.filter { it.isLowStock }
            if (low.isNotEmpty()) {
                item {
                    val lines = low.map { stringResource(R.string.x_x_left, it.name, app.vzato.ui.amountText(it.stockCount, it.unit)) }
                    Banner(lines.joinToString("\n"), Palette.overdue)
                }
            }
            if (slots.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.whole_day).uppercase(locale),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(slots, key = { it.id }) { slot ->
                    DoseRow(
                        slot = slot,
                        now = now,
                        grace = grace,
                        profileName = if (graph.hasSeveralProfiles) graph.profile(slot.medication.profileId)?.name else null,
                        onTaken = { requestTake(slot) },
                        onSkip = { vm.skip(SlotRef.of(slot)) },
                        onReset = { vm.reset(listOf(SlotRef.of(slot))) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    pending?.let { take ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.take_this_dose)) },
            text = { Text(take.message) },
            confirmButton = {
                TextButton(onClick = { pending = null; take.onConfirm() }) {
                    Text(stringResource(R.string.take_anyway), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showingExtra) {
        ExtraDoseDialog(medications, zone, onDismiss = { showingExtra = false }) { medication, at ->
            showingExtra = false
            when (val verdict = DoseGuard.verdictForExtraDose(medication, at)) {
                is DoseGuard.Verdict.RecentlyTaken -> pending = PendingTake(
                    recentTemplate.format(medication.name, timeText(verdict.at, locale, zone)),
                ) { vm.extraDose(medication.id, at) }
                else -> vm.extraDose(medication.id, at)
            }
        }
    }
}

/** "Before a meal" doses taken today whose waiting time still runs: the latest time and the names. */
private fun mealWait(slots: List<DoseSlot>, now: Instant, zone: java.time.ZoneId): Pair<Instant, String>? {
    val waits = slots.mapNotNull { slot ->
        val event = slot.event ?: return@mapNotNull null
        val ready = DoseScheduler.mealReadyTime(event, zone) ?: return@mapNotNull null
        if (ready > now) ready to slot.medication.name else null
    }
    if (waits.isEmpty()) return null
    return waits.maxOf { it.first } to waits.map { it.second }.distinct().joinToString(", ")
}

@Composable
fun DoseCard(
    slot: DoseSlot,
    now: Instant,
    grace: Duration,
    showProfile: Boolean,
    profileName: String?,
    mode: TakeMode,
    onTaken: () -> Unit,
    onSkip: () -> Unit,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
) {
    val locale = locale()
    val status = slot.status(now, grace)
    val isMissed = status == DoseStatus.MISSED
    val isOverdue = slot.isOverdue(now, grace)
    val accent = when {
        isMissed -> Palette.missed
        isOverdue -> Palette.overdue
        else -> MaterialTheme.colorScheme.onSurface
    }
    val zone = java.time.ZoneId.systemDefault()
    val meal = DoseScheduler.schedule(slot.medication, slot.scheduledAt, zone)?.mealRelation
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, accent), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = when {
                isMissed -> R.string.missed
                isOverdue -> R.string.overdue
                else -> R.string.next
            }
            Text(
                "${timeText(slot.scheduledAt, locale)} · ${stringResource(label)}".uppercase(locale),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isMissed || isOverdue) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(14.dp).clip(CircleShape).background(Palette.fromHex(slot.medication.colorHex)))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showProfile && profileName != null) {
                Text(profileName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                slot.medication.name,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 36.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(doseAmountText(slot.medication), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val details = listOfNotNull(
                slot.medication.dosageText.takeIf { it.isNotBlank() },
                meal?.takeIf { it != MealRelation.ANY }?.let { stringResource(mealLabel(it)) },
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (mode == TakeMode.DIRECT) {
            Button(
                onClick = onTaken,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.taken, contentColor = Color.White),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.taken).uppercase(locale), fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onSnooze, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.snooze_n_min, snoozeMinutes), maxLines = 1)
                }
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.skip), maxLines = 1)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.next_dose_at_x, timeText(slot.scheduledAt, locale)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (mode == TakeMode.ASK_FIRST) {
                OutlinedButton(onClick = onTaken, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.taken_2))
                }
            }
        }
    }
}

/** One dose in the whole-day list. Touch and hold for Taken, Skip or Reset. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DoseRow(
    slot: DoseSlot,
    now: Instant,
    grace: Duration,
    profileName: String?,
    onTaken: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    showsTakeButton: Boolean = true,
) {
    val locale = locale()
    val status = slot.status(now, grace)
    val isOverdue = slot.isOverdue(now, grace)
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { menu = true })
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Wide enough for "10:28 AM" in 12-hour languages, never wrapped.
            Text(
                timeText(slot.scheduledAt, locale), modifier = Modifier.widthIn(min = 56.dp).padding(end = 8.dp),
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false,
            )
            StatusMark(status, isOverdue)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (profileName != null) {
                    Text(profileName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.fromHex(slot.medication.colorHex)))
                    Spacer(Modifier.width(6.dp))
                    Text(slot.medication.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (slot.isUnscheduled) {
                        Text(" " + stringResource(R.string.extra), style = MaterialTheme.typography.labelSmall)
                    }
                }
                val amount = slot.event?.takeIf { it.status == DoseStatus.TAKEN }?.let { app.vzato.ui.amountText(it.takenUnits, it.takenUnit) }
                    ?: doseAmountText(slot.medication)
                Text(
                    listOfNotNull(amount, slot.medication.dosageText.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val takenAt = slot.event?.takenAt
                when {
                    status == DoseStatus.TAKEN && takenAt != null -> Text(
                        stringResource(R.string.taken_at) + " " + timeText(takenAt, locale),
                        style = MaterialTheme.typography.bodySmall, color = Palette.taken,
                    )
                    status == DoseStatus.SKIPPED -> Text(stringResource(R.string.skipped), style = MaterialTheme.typography.bodySmall, color = Palette.skipped)
                    status == DoseStatus.MISSED -> Text(stringResource(R.string.missed), style = MaterialTheme.typography.bodySmall, color = Palette.missed)
                    isOverdue -> Text(stringResource(R.string.overdue), style = MaterialTheme.typography.bodySmall, color = Palette.overdue)
                }
            }
            if (showsTakeButton && slot.isCurrent(now)) {
                Button(
                    onClick = onTaken,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.taken, contentColor = Color.White),
                ) { Text(stringResource(R.string.taken)) }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (status != DoseStatus.TAKEN) {
                DropdownMenuItem(text = { Text(stringResource(R.string.taken)) }, onClick = { menu = false; onTaken() })
            }
            if (status != DoseStatus.SKIPPED && !slot.isUnscheduled) {
                DropdownMenuItem(text = { Text(stringResource(R.string.skip)) }, onClick = { menu = false; onSkip() })
            }
            if (slot.event != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.reset)) }, onClick = { menu = false; onReset() })
            }
        }
    }
}

@Composable
private fun StatusMark(status: DoseStatus, isOverdue: Boolean) {
    val (symbol, color) = when {
        status == DoseStatus.TAKEN -> "✓" to Palette.taken
        status == DoseStatus.SKIPPED -> "»" to Palette.skipped
        status == DoseStatus.MISSED -> "✕" to Palette.missed
        isOverdue -> "!" to Palette.overdue
        else -> "○" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier.size(24.dp).border(BorderStroke(1.5.dp, color), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
private fun Banner(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun AllDoneCard() {
    Column(
        Modifier.fillMaxWidth().border(BorderStroke(2.dp, Palette.taken), RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("✓ " + stringResource(R.string.all_done_for_today), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Palette.taken)
        Text(stringResource(R.string.every_scheduled_dose_is_recorded), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyToday(noMedications: Boolean, onAddMedication: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.no_doses_today), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(if (noMedications) R.string.add_your_first_medication_with_its_dose_and_times else R.string.nothing_is_scheduled_for_today),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (noMedications) {
            Button(onClick = onAddMedication, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.add_medication), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
