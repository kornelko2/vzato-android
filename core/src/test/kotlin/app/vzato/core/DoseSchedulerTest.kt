// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** VzatoTests/DoseSchedulerTests.swift, StockTests.swift and DoseGuardTests.swift. */
class DoseSchedulerTest {
    @Test fun generatesSlotsForEachTime() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60, 20 * 60))
        val slots = slots(medication, monday)
        assertEquals(listOf(at(monday, 8), at(monday, 20)), slots.map { it.scheduledAt })
        assertTrue(slots.all { it.event == null })
    }

    @Test fun respectsWeekdays() {
        val store = TestStore()
        // Weekday 2 = Monday, 4 = Wednesday (iOS numbering)
        val medication = store.medication(times = listOf(9 * 60), weekdays = listOf(2, 4))
        assertEquals(1, slots(medication, monday).size)
        assertTrue(slots(medication, days(1, monday)).isEmpty())
        assertEquals(1, slots(medication, days(2, monday)).size)
    }

    @Test fun respectsStartAndEndDates() {
        val store = TestStore()
        val medication = store.medication(times = listOf(9 * 60), start = days(1, monday), end = days(2, monday))
        assertTrue(slots(medication, monday).isEmpty())
        assertEquals(1, slots(medication, days(1, monday)).size)
        assertEquals(1, slots(medication, days(2, monday)).size)
        assertTrue(slots(medication, days(3, monday)).isEmpty())
    }

    @Test fun pausedAndArchivedMedicationsStopGeneratingDoses() {
        val store = TestStore()
        val medication = store.medication(times = listOf(9 * 60))
        medication.schedules.first().isPaused = true
        assertTrue(slots(medication, monday).isEmpty())
        medication.schedules.first().isPaused = false
        medication.archivedAt = days(1, monday).start(zone)
        assertEquals(1, slots(medication, days(1, monday)).size)
        assertTrue(slots(medication, days(2, monday)).isEmpty())
    }

    @Test fun matchesRecordedEventsAndKeepsExtraDoses() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60, 20 * 60))
        val logger = store.logger(at(monday, 8, 5))
        logger.markTaken(slots(medication, monday)[0])
        logger.logExtraDose(medication, at(monday, 14))
        val slots = slots(medication, monday)
        assertEquals(3, slots.size)
        assertEquals(DoseStatus.TAKEN, slots[0].event?.status)
        assertTrue(slots[1].isUnscheduled)
        assertNull(slots[2].event)
    }

    @Test fun pendingBecomesMissedAfterGrace() {
        val store = TestStore()
        val slot = slots(store.medication(times = listOf(8 * 60)), monday)[0]
        assertEquals(DoseStatus.PENDING, slot.status(at(monday, 7), twoHours))
        assertTrue(slot.isOverdue(at(monday, 9), twoHours))
        assertEquals(DoseStatus.MISSED, slot.status(at(monday, 10, 1), twoHours))
    }

    @Test fun slotsInIntervalSpanDays() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60, 20 * 60))
        val slots = DoseScheduler.slots(listOf(medication), monday.start(zone), at(days(2, monday), 12), zone)
        assertEquals(5, slots.size)
    }

    @Test fun dosesBeforeTheMedicationWasAddedAreNotMissed() {
        val store = TestStore()
        val medication = store.medication(times = listOf(7 * 60, 12 * 60, 19 * 60))
        medication.createdAt = at(monday, 9, 35)
        assertEquals(listOf(at(monday, 12), at(monday, 19)), slots(medication, monday).map { it.scheduledAt })
        assertEquals(3, slots(medication, days(1, monday)).size)
    }

    @Test fun aDoseRecordedBeforeAddingStillShows() {
        val store = TestStore()
        val medication = store.medication(times = listOf(7 * 60, 12 * 60))
        store.logger(at(monday, 9, 40)).markTaken(slots(medication, monday)[0], at(monday, 7))
        medication.createdAt = at(monday, 9, 35)
        val slots = slots(medication, monday)
        assertEquals(at(monday, 7), slots.first().scheduledAt)
        assertEquals(DoseStatus.TAKEN, slots.first().event?.status)
    }

    @Test fun doseMovedAfterTakingGetsNoReminder() {
        val store = TestStore()
        val medication = store.medication(name = "Letrox", times = listOf(8 * 60))
        store.logger(at(monday, 8, 5)).markTaken(slots(medication, monday)[0])
        medication.schedules.first().minutesOfDay = listOf(9 * 60)
        val nine = slots(medication, monday).first { it.scheduledAt == at(monday, 9) }
        assertNull(nine.event)
        assertTrue(DoseScheduler.isCoveredByMovedDose(nine, zone))
        val tomorrow = slots(medication, days(1, monday))[0]
        assertFalse(DoseScheduler.isCoveredByMovedDose(tomorrow, zone))
    }

    @Test fun aLateDoseDoesNotSilenceTheNextOne() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60, 12 * 60))
        store.logger(at(monday, 10, 30)).markTaken(slots(medication, monday)[0])
        val noon = slots(medication, monday)[1]
        assertFalse(DoseScheduler.isCoveredByMovedDose(noon, zone))
    }

    @Test fun clockChangeDayKeepsTheWallClockTime() {
        // Not on iOS: 25 Oct 2026 has 25 hours in Prague; 8:00 stays 8:00.
        val store = TestStore()
        val sunday = java.time.LocalDate.of(2026, 10, 25)
        val medication = store.medication(times = listOf(8 * 60), start = sunday.minusDays(1))
        assertEquals(8 * 60, slots(medication, sunday)[0].scheduledAt.minutesOfDay(zone))
    }
}

class StockTest {
    @Test fun takingLowersStockAndResetRestoresIt() {
        val store = TestStore()
        val medication = store.medication(stock = 10.0, unitsPerDose = 2.0)
        val logger = store.logger(at(monday, 8))
        val slot = slots(medication, monday)[0]
        val event = logger.markTaken(slot)
        assertEquals(8.0, medication.stockCount, 0.0)
        logger.markSkipped(DoseSlot(medication, slot.scheduledAt, event))
        assertEquals(10.0, medication.stockCount, 0.0)
        logger.markTaken(DoseSlot(medication, slot.scheduledAt, event))
        assertEquals(8.0, medication.stockCount, 0.0)
        logger.reset(DoseSlot(medication, slot.scheduledAt, event))
        assertEquals(10.0, medication.stockCount, 0.0)
        assertTrue(medication.events.isEmpty())
    }

    @Test fun lowStockNotifiesOnceUntilRefilled() {
        val store = TestStore()
        val medication = store.medication(stock = 6.0)
        medication.lowStockThreshold = 5.0
        val notifier = SpyNotifier()
        val logger = store.logger(at(monday, 8), notifier)
        val slots = slots(medication, monday)
        logger.markTaken(slots[0])
        assertEquals(listOf("Ibuprofen"), notifier.lowStockNames)
        logger.markTaken(slots[1])
        assertEquals(1, notifier.lowStockNames.size)
        StockService.setStock(medication, 30.0, at(monday, 21), store)
        assertNull(medication.lowStockNotifiedAt)
        medication.stockCount = 5.0
        StockService.checkLowStock(medication, at(monday, 22), notifier)
        assertEquals(2, notifier.lowStockNames.size)
    }

    @Test fun projectedRunOutIsFirstUncoveredDose() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60, 20 * 60), stock = 3.0)
        // Mon 8, Mon 20, Tue 8 are covered; Tue 20 is the first that is not.
        assertEquals(at(days(1, monday), 20), StockService.projectedRunOutDate(medication, at(monday, 7), zone))
    }

    @Test fun noRunOutWithoutStockTrackingOrSchedule() {
        val store = TestStore()
        assertNull(StockService.projectedRunOutDate(store.medication(name = "A"), monday.start(zone), zone))
        val plenty = store.medication(name = "B", times = listOf(8 * 60), stock = 1000.0)
        assertNull(StockService.projectedRunOutDate(plenty, monday.start(zone), zone))
    }

    @Test fun halfTabletDosesLowerStockByHalf() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60, 20 * 60), stock = 10.0, unitsPerDose = 0.5)
        val logger = store.logger(at(monday, 8))
        val slots = slots(medication, monday)
        logger.markTaken(slots[0])
        assertEquals(9.5, medication.stockCount, 0.0)
        assertEquals(19, medication.dosesInStock)
        logger.markTaken(slots[1])
        assertEquals(9.0, medication.stockCount, 0.0)
    }

    @Test fun runOutAccountsForQuarterTablets() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60), stock = 1.0, unitsPerDose = 0.25)
        assertEquals(at(days(4, monday), 8), StockService.projectedRunOutDate(medication, at(monday, 7), zone))
    }

    @Test fun reconciliationRoundsToWholeDoses() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60), stock = 10.0, unitsPerDose = 0.5)
        val report = ReconciliationService.report(medication, 8.5, at(days(3, monday), 12), zone)
        assertEquals(1.5, report.unitDifference!!, 0.0)
        assertEquals(ReconciliationReport.Verdict.UnrecordedDoses(3), report.verdict)
    }
}

class DoseGuardTest {
    private fun degan(store: TestStore) = store.medication(name = "Degan", times = listOf(7 * 60, 12 * 60, 19 * 60))

    @Test fun dueDoseIsFine() {
        val store = TestStore()
        assertEquals(DoseGuard.Verdict.Ok, DoseGuard.verdict(slots(degan(store), monday)[0], at(monday, 7, 25)))
    }

    @Test fun secondTapOnTheNextDoseAsksFirst() {
        val store = TestStore()
        val medication = degan(store)
        val slots = slots(medication, monday)
        store.logger(at(monday, 7, 25)).markTaken(slots[0])
        assertEquals(DoseGuard.Verdict.RecentlyTaken(at(monday, 7, 25)), DoseGuard.verdict(slots[1], at(monday, 7, 25)))
    }

    @Test fun doseFarAheadIsTooEarlyEvenWithoutRecentIntake() {
        val store = TestStore()
        val slots = slots(degan(store), monday)
        assertEquals(DoseGuard.Verdict.TooEarly(at(monday, 12)), DoseGuard.verdict(slots[1], at(monday, 9)))
        assertEquals(DoseGuard.Verdict.Ok, DoseGuard.verdict(slots[1], at(monday, 11, 30)))
        assertEquals(DoseGuard.Verdict.TooEarly(at(monday, 12)), DoseGuard.verdict(slots[1], at(monday, 11, 15)))
        assertEquals(DoseGuard.Verdict.Ok, DoseGuard.verdict(slots[1], at(monday, 15)))
    }

    @Test fun gapIsHalfTheShortestInterval() {
        val store = TestStore()
        assertEquals(java.time.Duration.ofMinutes(150), DoseGuard.minimumGap(degan(store)))
        assertEquals(java.time.Duration.ofHours(3), DoseGuard.minimumGap(store.medication(name = "A", times = listOf(8 * 60))))
        assertEquals(java.time.Duration.ofHours(1), DoseGuard.minimumGap(store.medication(name = "B", times = listOf(8 * 60, 9 * 60))))
    }

    @Test fun normalNextDoseAfterTheGapIsFine() {
        val store = TestStore()
        val medication = degan(store)
        val slots = slots(medication, monday)
        store.logger(at(monday, 7)).markTaken(slots[0])
        assertEquals(DoseGuard.Verdict.Ok, DoseGuard.verdict(slots[1], at(monday, 12)))
    }

    @Test fun extraDoseRightAfterAPlannedOneAsks() {
        val store = TestStore()
        val medication = degan(store)
        store.logger(at(monday, 7)).markTaken(slots(medication, monday)[0])
        assertEquals(DoseGuard.Verdict.RecentlyTaken(at(monday, 7)), DoseGuard.verdictForExtraDose(medication, at(monday, 8)))
        assertEquals(DoseGuard.Verdict.Ok, DoseGuard.verdictForExtraDose(medication, at(monday, 10)))
    }

    @Test fun widgetOffersOneTapOnlyWhenSafe() {
        val store = TestStore()
        val medication = degan(store)
        store.logger(at(monday, 7, 25)).markTaken(slots(medication, monday)[0])
        val entry = TodaySnapshot.entries(listOf(medication), at(monday, 7, 26), twoHours, zone)[0]
        assertEquals(at(monday, 12), entry.featured?.scheduledAt)
        assertEquals(false, entry.featured?.canTakeNow)
    }
}
