// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

/** A backup restored on another phone gives back the same medications, history and supply. */
@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val first = VzatoDatabase.inMemory(context)
    private val second = VzatoDatabase.inMemory(context)

    @After fun close() {
        first.close()
        second.close()
    }

    @Test fun backupRoundTrips() = runTest {
        val texts = AndroidActivityTexts(context)
        val a = VzatoRepository(first, "A", texts, "Me")
        a.edit { graph, store -> SampleData.populate(graph, store, Instant.now(), ZoneId.of("Europe/Prague")) }
        val file = a.exportBackup("0.1")
        assertTrue(file.contains("\"format\": \"vzato-backup\""))

        val b = VzatoRepository(second, "B", texts, "Me")
        b.restoreBackup(file)
        val before = a.load()
        val after = b.load()
        assertEquals(before.medications.map { it.name }.sorted(), after.medications.map { it.name }.sorted())
        assertEquals(before.medications.sumOf { it.events.size }, after.medications.sumOf { it.events.size })
        assertEquals(before.medications.map { it.stockCount }.sorted(), after.medications.map { it.stockCount }.sorted())
        assertEquals(a.activity(5000).size, b.activity(5000).size)
        assertEquals(1, after.profiles.count { it.isDefault })
    }

    @Test(expected = IllegalArgumentException::class)
    fun otherFilesAreRefused() = runTest {
        VzatoRepository(second, "B", AndroidActivityTexts(context), "Me").restoreBackup("{\"hello\": 1}")
    }
}
