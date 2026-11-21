// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.vzato.core.ActivityKind
import app.vzato.core.DoseLogger
import app.vzato.core.DoseNotifying
import app.vzato.core.DoseScheduler
import app.vzato.core.DoseStatus
import app.vzato.core.DoseUnit
import app.vzato.core.Medication
import app.vzato.core.Schedule
import app.vzato.core.StockService
import app.vzato.core.at
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** The core logic saved through Room and read back: what the app does on every tap. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone = ZoneId.of("Europe/Prague")
    private val monday = LocalDate.of(2026, 11, 2)
    private val quiet = object : DoseNotifying {
        override fun notifyLowStock(medication: Medication) {}
    }
    private lateinit var db: VzatoDatabase
    private lateinit var repository: VzatoRepository

    @Before fun open() {
        db = VzatoDatabase.inMemory(context)
        repository = VzatoRepository(db, "Test", AndroidActivityTexts(context), "Me")
    }

    @After fun close() = db.close()

    private suspend fun addLetrox() = repository.edit { graph, store ->
        val medication = Medication(name = "Letrox", dosageText = "50 µg", unitsPerDose = 0.5, profileId = graph.defaultProfile.id,
            createdAt = monday.at(0, zone))
        medication.schedules += Schedule(minutesOfDay = listOf(8 * 60), startDate = monday.at(0, zone))
        StockService.setStock(medication, 10.0, monday.at(0, zone), store)
        graph.medications += medication
        medication.id
    }

    @Test fun aDefaultProfileExistsFromTheStart() = runTest {
        val graph = repository.load()
        assertEquals(1, graph.profiles.size)
        assertTrue(graph.defaultProfile.isDefault)
        assertEquals(1, repository.load().profiles.size)
    }

    @Test fun takenDoseIsSavedWithItsAmountAndLowersTheSupply() = runTest {
        val id = addLetrox()
        repository.edit { graph, store ->
            val medication = graph.medication(id)!!
            val slot = DoseScheduler.slots(listOf(medication), monday, zone)[0]
            DoseLogger(store, monday.at(8 * 60 + 5, zone), quiet, zone).markTaken(slot)
        }
        val medication = repository.load().medication(id)!!
        assertEquals(9.5, medication.stockCount, 0.0)
        assertEquals(DoseUnit.TABLET, medication.unit)
        val event = medication.events.single()
        assertEquals(DoseStatus.TAKEN, event.status)
        assertEquals(0.5, event.units, 0.0)
        assertEquals("Test", event.deviceName)
        assertEquals(listOf(ActivityKind.TAKEN, ActivityKind.STOCK_COUNTED), repository.activity().map { it.kind })
    }

    @Test fun undoDeletesTheRecordAndReturnsTheSupply() = runTest {
        val id = addLetrox()
        val now = monday.at(8 * 60 + 5, zone)
        repository.edit { graph, store ->
            DoseLogger(store, now, quiet, zone).markTaken(DoseScheduler.slots(listOf(graph.medication(id)!!), monday, zone)[0])
        }
        repository.edit { graph, store ->
            DoseLogger(store, now, quiet, zone).reset(DoseScheduler.slots(listOf(graph.medication(id)!!), monday, zone)[0])
        }
        val medication = repository.load().medication(id)!!
        assertTrue(medication.events.isEmpty())
        assertEquals(10.0, medication.stockCount, 0.0)
    }

    @Test fun planChangesAndDeletionAreSaved() = runTest {
        val id = addLetrox()
        repository.edit { graph, _ ->
            val medication = graph.medication(id)!!
            medication.schedules.clear()
            medication.schedules += Schedule(minutesOfDay = listOf(7 * 60, 19 * 60), weekdays = listOf(2, 4))
        }
        val schedule = repository.load().medication(id)!!.schedules.single()
        assertEquals(listOf(7 * 60, 19 * 60), schedule.minutesOfDay)
        assertEquals(listOf(2, 4), schedule.weekdays)
        repository.deleteMedication(id)
        assertTrue(repository.load().medications.isEmpty())
    }
}
