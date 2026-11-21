// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The CSV cases of the iOS tests (ReconciliationTests, DoseAmountTests, ProfileTests). */
class CsvExportTest {
    @Test fun csvEscapesCommasAndQuotes() {
        assertEquals("plain", CsvExport.escape("plain"))
        assertEquals("\"a,b\"", CsvExport.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.escape("say \"hi\""))
    }

    @Test fun csvHasTheAmountTaken() {
        val store = TestStore()
        val medication = store.medication(name = "Letrox", times = listOf(8 * 60), unitsPerDose = 0.5)
        val event = store.logger(at(monday, 8, 5)).markTaken(slots(medication, monday)[0])
        val csv = CsvExport.make(listOf(event))
        assertTrue(csv.startsWith(CsvExport.HEADER + "\n"))
        assertTrue(csv.trimEnd().lines().last().endsWith(",0.5,tablet"))
        assertTrue(csv.contains("2026-11-02T07:00:00Z,taken,2026-11-02T07:05:00Z,app,0,"))
    }

    @Test fun exportNamesTheProfileAndListsMissedDoses() {
        val store = TestStore()
        val medication = store.medication(name = "Mentík", times = listOf(8 * 60, 20 * 60))
        val event = store.logger(at(monday, 8, 5)).markTaken(slots(medication, monday)[0])
        val missed = CsvExport.missedSlots(listOf(medication), at(monday, 23), twoHours, zone)
        assertEquals(listOf(at(monday, 20)), missed.map { it.scheduledAt })
        val lines = CsvExport.make(listOf(event), missed) { "Rex" }.trimEnd().lines()
        assertEquals(3, lines.size)
        assertTrue(lines[1].endsWith(",Rex,1,tablet"))
        assertTrue(lines[2].contains(",missed,,,0,Rex,,"))
    }

    @Test fun amountsArePlainNumbers() {
        assertEquals("1", CsvExport.amountNumber(1.0))
        assertEquals("0.25", CsvExport.amountNumber(0.25))
        assertEquals("1.5", CsvExport.amountNumber(1.5))
    }

    @Test fun activityLogIsNewestFirst() {
        val store = TestStore()
        val medication = store.medication(name = "Letrox", times = listOf(8 * 60))
        store.logger(at(monday, 8, 5)).markTaken(slots(medication, monday)[0])
        val csv = CsvExport.activity(store.activity).trimEnd().lines()
        assertEquals("date,action,medication,dose_time,device,source,detail", csv[0])
        assertTrue(csv[1].startsWith("2026-11-02T07:05:00Z,taken,Letrox,2026-11-02T07:00:00Z,Test device,app,"))
        assertEquals(2, csv.size)
    }
}
