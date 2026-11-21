// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.data

import app.vzato.core.ActivityEntry
import app.vzato.core.ActivityTexts
import app.vzato.core.DoseEvent
import app.vzato.core.DoseStore
import app.vzato.core.Medication
import app.vzato.core.Profile
import app.vzato.core.ProfileKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/** Everything the screens and the logic work on, loaded at once. Small: one person's medications. */
class Graph(
    val profiles: MutableList<Profile>,
    val medications: MutableList<Medication>,
) {
    val defaultProfile: Profile get() = profiles.first { it.isDefault }
    val activeMedications: List<Medication> get() = medications.filter { !it.isArchived }
    val archivedMedications: List<Medication> get() = medications.filter { it.isArchived }
    fun medication(id: UUID): Medication? = medications.firstOrNull { it.id == id }
    fun profile(id: UUID?): Profile? = profiles.firstOrNull { it.id == id }
    val hasSeveralProfiles: Boolean get() = profiles.size > 1
}

/**
 * The only way in and out of the database. Every change runs through [edit]: one at a time, in one
 * transaction, on a fresh copy of the data, so a notification action and a tap on the screen can
 * never overwrite each other.
 */
class VzatoRepository(
    private val db: VzatoDatabase,
    private val deviceName: String,
    private val texts: ActivityTexts,
    private val defaultProfileName: String,
) {
    private val mutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val _version = MutableStateFlow(0L)

    /** Grows after every change; screens reload when it does. */
    val version: StateFlow<Long> = _version.asStateFlow()

    suspend fun load(): Graph = mutex.withLock {
        withContext(io) { db.runInTransaction<Graph> { RoomDoseStore(db.dao(), deviceName, texts, defaultProfileName).graph } }
    }

    /** Runs `block` on a fresh copy of the data and saves what it changed. */
    suspend fun <T> edit(block: (Graph, DoseStore) -> T): T {
        val result = mutex.withLock {
            withContext(io) {
                db.runInTransaction<T> {
                    val store = RoomDoseStore(db.dao(), deviceName, texts, defaultProfileName)
                    val value = block(store.graph, store)
                    store.save()
                    value
                }
            }
        }
        _version.value++
        return result
    }

    /** Deletes a medication with its plan and dose records. The activity log keeps its lines. */
    suspend fun deleteMedication(id: UUID) {
        mutex.withLock { withContext(io) { db.dao().deleteMedication(id.toString()) } }
        _version.value++
    }

    suspend fun activity(limit: Int = 500): List<ActivityEntry> =
        withContext(io) { db.dao().activity(limit).map { it.toCore() } }
}

/**
 * Loads the graph and remembers each row as read; [save] writes only the rows that differ and
 * deletes the ones that disappeared from a medication. Not thread-safe: one per edit.
 */
internal class RoomDoseStore(
    private val dao: VzatoDao,
    override val deviceName: String,
    override val texts: ActivityTexts,
    defaultProfileName: String,
) : DoseStore {
    val graph: Graph
    private val profiles = mutableMapOf<String, ProfileEntity>()
    private val medications = mutableMapOf<String, MedicationEntity>()
    private val schedules = mutableMapOf<String, ScheduleEntity>()
    private val events = mutableMapOf<String, DoseEventEntity>()
    private val newActivity = mutableListOf<ActivityEntry>()
    private val deletedEvents = mutableSetOf<String>()

    init {
        dao.profiles().forEach { profiles[it.id] = it }
        dao.medications().forEach { medications[it.id] = it }
        dao.schedules().forEach { schedules[it.id] = it }
        dao.events().forEach { events[it.id] = it }
        val coreMedications = medications.values.map { it.toCore() }
        val byId = coreMedications.associateBy { it.id.toString() }
        schedules.values.forEach { row -> byId[row.medicationId]?.schedules?.add(row.toCore()) }
        events.values.forEach { row -> byId[row.medicationId]?.let { it.events += row.toCore(it) } }
        graph = Graph(profiles.values.map { it.toCore() }.toMutableList(), coreMedications.sortedBy { it.name.lowercase() }.toMutableList())
        if (graph.profiles.none { it.isDefault }) {
            val now = Instant.now()
            graph.profiles += Profile(name = defaultProfileName, kind = ProfileKind.PERSON, isDefault = true, createdAt = now, updatedAt = now)
            save()
        }
    }

    override fun inserted(event: DoseEvent) {}

    override fun deleted(event: DoseEvent) {
        deletedEvents += event.id.toString()
    }

    override fun log(entry: ActivityEntry) {
        newActivity += entry
    }

    override fun save() {
        changed(graph.profiles.map { it.toEntity() }, profiles)?.let(dao::upsertProfiles)
        changed(graph.medications.map { it.toEntity() }, medications)?.let(dao::upsertMedications)

        val currentSchedules = graph.medications.flatMap { m -> m.schedules.map { it.toEntity(m.id) } }
        changed(currentSchedules, schedules)?.let(dao::upsertSchedules)
        val keptMedications = graph.medications.map { it.id.toString() }.toSet()
        val goneSchedules = schedules.values
            .filter { it.medicationId in keptMedications && currentSchedules.none { s -> s.id == it.id } }
            .map { it.id }
        if (goneSchedules.isNotEmpty()) {
            dao.deleteSchedules(goneSchedules)
            goneSchedules.forEach(schedules::remove)
        }

        val currentEvents = graph.medications.flatMap { m -> m.events.map { it.toEntity(m.id) } }
        changed(currentEvents, events)?.let(dao::upsertEvents)
        val gone = deletedEvents.filter { events.containsKey(it) }
        if (gone.isNotEmpty()) {
            dao.deleteEvents(gone)
            gone.forEach(events::remove)
        }
        deletedEvents.clear()

        if (newActivity.isNotEmpty()) {
            dao.insertActivity(newActivity.map { it.toEntity() })
            newActivity.clear()
        }
    }

    /** Rows that are new or differ from what was read; updates the remembered copy. */
    private fun <E : Any> changed(rows: List<E>, known: MutableMap<String, E>): List<E>? {
        val id: (E) -> String = { row ->
            when (row) {
                is ProfileEntity -> row.id
                is MedicationEntity -> row.id
                is ScheduleEntity -> row.id
                is DoseEventEntity -> row.id
                else -> error("unknown row")
            }
        }
        val different = rows.filter { known[id(it)] != it }
        different.forEach { known[id(it)] = it }
        return different.takeIf { it.isNotEmpty() }
    }
}
