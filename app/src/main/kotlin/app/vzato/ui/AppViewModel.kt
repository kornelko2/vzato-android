// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.vzato.VzatoApp
import app.vzato.core.DoseLogger
import app.vzato.core.DoseNotifying
import app.vzato.core.DoseScheduler
import app.vzato.core.DoseSlot
import app.vzato.core.DoseSource
import app.vzato.core.DoseStore
import app.vzato.core.Medication
import app.vzato.core.MedicationArchive
import app.vzato.core.ReconciliationService
import app.vzato.core.day
import app.vzato.data.Graph
import app.vzato.data.SampleData
import app.vzato.reminders.ReminderStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Points at a dose across reloads: the objects in a screen's graph are replaced after each change. */
data class SlotRef(val medicationId: UUID, val scheduledAt: Instant, val eventId: UUID?, val unscheduled: Boolean) {
    companion object {
        fun of(slot: DoseSlot) = SlotRef(slot.medication.id, slot.scheduledAt, slot.event?.id, slot.isUnscheduled)
    }
}

/** App-wide state for the screens: the data, the clock, and every action, all through the core logic. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = VzatoApp.of(application)
    private val repository = app.repository
    val settings = app.settings

    private val _graph = MutableStateFlow<Graph?>(null)
    val graph: StateFlow<Graph?> = _graph.asStateFlow()

    private val _now = MutableStateFlow(Instant.now())

    /** Ticks on every full and half minute, so a dose turns due right on its minute. */
    val now: StateFlow<Instant> = _now.asStateFlow()

    val zone: ZoneId get() = ZoneId.systemDefault()

    private val reminders = app.reminders
    private val notifier: DoseNotifying get() = reminders.notifier

    private val _reminderStatus = MutableStateFlow(reminders.status())

    /** Permission state for the warnings on Today. */
    val reminderStatus: StateFlow<ReminderStatus> = _reminderStatus.asStateFlow()

    init {
        viewModelScope.launch { repository.version.collect { _graph.value = repository.load() } }
        viewModelScope.launch {
            while (true) {
                _now.value = Instant.now()
                delay(millisToNextTick(_now.value))
            }
        }
    }

    /** After the app comes back: the ticker may have slept while the app was away. */
    fun refreshClock() {
        _now.value = Instant.now()
        _reminderStatus.value = reminders.status()
        // Permissions may have changed in the system settings meanwhile.
        viewModelScope.launch { reminders.rebuild() }
    }

    /** Snooze from the Today card: only this medication; the others of its time keep their reminder. */
    fun snooze(refs: List<SlotRef>) {
        viewModelScope.launch {
            val graph = repository.load()
            reminders.snooze(refs.mapNotNull { find(graph, it) })
        }
    }

    fun take(refs: List<SlotRef>, onDone: (List<SlotRef>) -> Unit = {}) = edit { graph, store ->
        val now = Instant.now()
        val logger = DoseLogger(store, now, notifier, zone)
        refs.mapNotNull { ref -> find(graph, ref)?.let { logger.markTaken(it) } }
            .map { SlotRef(it.medication!!.id, it.scheduledAt!!, it.id, it.isUnscheduled) }
            .also { taken -> viewModelScope.launch { onDone(taken) } }
    }

    /** A dose confirmed afterwards (History): taken at its planned time. */
    fun takeAt(ref: SlotRef, at: Instant) = edit { graph, store ->
        find(graph, ref)?.let { DoseLogger(store, Instant.now(), notifier, zone).markTaken(it, at) }
    }

    /** The counted supply, optionally recording the doses that were taken without a record. */
    fun applySupplyCheck(medicationId: UUID, counted: Double, recordMissing: Boolean) = edit { graph, store ->
        val medication = graph.medication(medicationId) ?: return@edit
        val now = Instant.now()
        val report = ReconciliationService.report(medication, counted, now, zone)
        ReconciliationService.apply(report, counted, recordMissing, store, now, zone, notifier)
    }

    fun skip(ref: SlotRef) = edit { graph, store ->
        find(graph, ref)?.let { DoseLogger(store, Instant.now(), notifier, zone).markSkipped(it) }
    }

    fun reset(refs: List<SlotRef>) = edit { graph, store ->
        val logger = DoseLogger(store, Instant.now(), notifier, zone)
        refs.forEach { ref -> find(graph, ref)?.let(logger::reset) }
    }

    fun extraDose(medicationId: UUID, at: Instant) = edit { graph, store ->
        graph.medication(medicationId)?.let { DoseLogger(store, Instant.now(), notifier, zone).logExtraDose(it, at, DoseSource.APP) }
    }

    fun saveMedication(draft: MedicationDraft, onSaved: (UUID) -> Unit = {}) = edit { graph, store ->
        val id = draft.save(graph, store, Instant.now())
        viewModelScope.launch { onSaved(id) }
    }

    /** Debug builds only, from MainActivity. */
    fun populateSampleData() = edit { graph, store -> SampleData.populate(graph, store, Instant.now(), zone) }

    /** Debug builds only: "Test" due in two minutes, so a reminder can be watched without waiting. */
    fun addReminderTest() = edit { graph, store ->
        val due = Instant.now().plusSeconds(120).atZone(zone)
        MedicationDraft(name = "Test", times = listOf(due.hour * 60 + due.minute), startDate = due.toLocalDate())
            .save(graph, store, Instant.now().minusSeconds(3600), zone)
    }

    fun archive(id: UUID) = edit { graph, _ -> graph.medication(id)?.let { MedicationArchive.archive(it, Instant.now()) } }

    fun restore(id: UUID) = edit { graph, _ -> graph.medication(id)?.let { MedicationArchive.restore(it, Instant.now()) } }

    fun delete(id: UUID) {
        viewModelScope.launch { repository.deleteMedication(id) }
    }

    private fun edit(block: (Graph, DoseStore) -> Unit) {
        viewModelScope.launch { repository.edit(block) }
    }

    /** The same dose in a freshly loaded graph. */
    private fun find(graph: Graph, ref: SlotRef): DoseSlot? {
        val medication = graph.medication(ref.medicationId) ?: return null
        return DoseScheduler.slots(listOf(medication), ref.scheduledAt.day(zone), zone).firstOrNull { slot ->
            if (ref.eventId != null && slot.event?.id == ref.eventId) return@firstOrNull true
            slot.scheduledAt == ref.scheduledAt && slot.isUnscheduled == ref.unscheduled &&
                (ref.eventId == null || slot.event == null || slot.event?.id == ref.eventId)
        }
    }

    companion object {
        fun millisToNextTick(now: Instant): Long {
            val inMinute = now.toEpochMilli() % 60_000
            val rest = (if (inMinute < 30_000) 30_000 else 60_000) - inMinute
            return maxOf(rest, 200)
        }
    }
}
