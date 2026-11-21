// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

/** VzatoTests/TodaySnapshotTests.swift. */
class TodaySnapshotTest {
    /** Doses at 8, 12 and 20; the 8:00 one was taken. */
    private fun morningTaken(): Medication {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60, 12 * 60, 20 * 60))
        store.logger(at(monday, 8, 5)).markTaken(slots(medication, monday)[0])
        return medication
    }

    @Test fun entriesAtEveryChangeOfState() {
        val entries = TodaySnapshot.entries(listOf(morningTaken()), at(monday, 10), twoHours, zone)
        assertEquals(listOf(at(monday, 10), at(monday, 12), at(monday, 14), at(monday, 20), at(monday, 22)), entries.map { it.date })
        assertTrue(entries.all { it.taken == 1 && it.total == 3 })
    }

    @Test fun featuredFollowsTheAppRule() {
        val entries = TodaySnapshot.entries(listOf(morningTaken()), at(monday, 10), twoHours, zone)
        assertEquals(at(monday, 12), entries[0].featured?.scheduledAt)
        assertEquals(false, entries[0].featured?.isOverdue)
        assertEquals(listOf(at(monday, 20)), entries[0].upcoming.map { it.scheduledAt })
        assertEquals(true, entries[1].featured?.isOverdue)
        assertEquals(DoseStatus.MISSED, entries[2].featured?.status)
        assertEquals(at(monday, 12), entries[2].featured?.scheduledAt)
        assertEquals(at(monday, 20), entries[3].featured?.scheduledAt)
    }

    @Test fun allDoneWhenEverythingIsRecorded() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60))
        store.logger(at(monday, 8)).markTaken(slots(medication, monday)[0])
        val entries = TodaySnapshot.entries(listOf(medication), at(monday, 9), twoHours, zone)
        assertEquals(1, entries.size)
        assertNull(entries[0].featured)
        assertEquals(1, entries[0].taken)
    }

    @Test fun currentDosesComeFirstAndAreMarked() {
        val medication = TestStore().medication(times = listOf(8 * 60, 9 * 60, 12 * 60))
        val entry = TodaySnapshot.entries(listOf(medication), at(monday, 9, 30), twoHours, zone)[0]
        assertEquals(at(monday, 8), entry.featured?.scheduledAt)
        assertEquals(true, entry.featured?.isCurrent)
        assertEquals(listOf(at(monday, 9), at(monday, 12)), entry.upcoming.map { it.scheduledAt })
        assertEquals(listOf(true, false), entry.upcoming.map { it.isCurrent })
    }

    @Test fun slotIsCurrentFromItsTimeUntilRecorded() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60))
        val slot = slots(medication, monday)[0]
        assertFalse(slot.isCurrent(at(monday, 7, 59)))
        assertTrue(slot.isCurrent(at(monday, 8)))
        assertTrue(slot.isCurrent(at(monday, 23)))
        val event = store.logger(at(monday, 8)).markTaken(slot)
        assertFalse(DoseSlot(medication, slot.scheduledAt, event).isCurrent(at(monday, 9)))
    }
}

/** VzatoTests/DoseSummaryTests.swift. */
class DoseSummaryTest {
    @Test fun countsEachStatusOnce() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60, 12 * 60, 16 * 60, 20 * 60))
        val logger = store.logger(at(monday, 17))
        val slots = slots(medication, monday)
        logger.markTaken(slots[0])
        logger.markSkipped(slots[1])
        assertEquals(DaySummary(4, 1, 1, 0, 2), DoseSummary.summarize(slots(medication, monday), at(monday, 17), twoHours))
        val later = DoseSummary.summarize(slots(medication, monday), at(monday, 23), twoHours)
        assertEquals(2, later.missed)
        assertEquals(0.25, later.adherence!!, 0.0)
    }

    @Test fun adherenceIgnoresPendingDoses() {
        assertNull(DaySummary(3, 0, 0, 0, 3).adherence)
        assertEquals(1.0, DaySummary(3, 2, 0, 0, 1).adherence!!, 0.0)
    }

    @Test fun byDayOnlyIncludesDaysWithDoses() {
        val medication = TestStore().medication(times = listOf(9 * 60), weekdays = listOf(2, 4))
        val byDay = DoseSummary.byDay(listOf(medication), YearMonth.of(2026, 11), at(monday, 12), twoHours, zone)
        assertEquals(9, byDay.size)
        assertEquals(1, byDay[monday]?.missed)
        assertEquals(1, byDay[days(2, monday)]?.pending)
        assertNull(byDay[days(1, monday)])
    }

    @Test fun totalsAddUp() {
        val total = DoseSummary.total(listOf(DaySummary(2, 2, 0, 0, 0), DaySummary(3, 1, 1, 1, 0)))
        assertEquals(DaySummary(5, 3, 1, 1, 0), total)
        assertEquals(0.6, total.adherence!!, 1e-9)
    }
}

/** VzatoTests/MealReminderTests.swift. */
class MealReminderTest {
    private fun beforeMeal(store: TestStore, wait: Int = 30): Medication {
        val medication = store.medication(name = "Letrox", times = listOf(7 * 60))
        medication.schedules.first().mealRelation = MealRelation.BEFORE
        medication.schedules.first().mealWaitMinutes = wait
        return medication
    }

    @Test fun takingABeforeMealDoseSchedulesTheMealReminder() {
        val store = TestStore()
        val medication = beforeMeal(store, 45)
        val spy = SpyNotifier()
        store.logger(at(monday, 7, 2), spy).markTaken(slots(medication, monday)[0])
        assertEquals(listOf("Letrox" to at(monday, 7, 47)), spy.mealReady)
    }

    @Test fun otherMealRelationsGetNoMealReminder() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60))
        medication.schedules.first().mealRelation = MealRelation.AFTER
        val spy = SpyNotifier()
        store.logger(at(monday, 8), spy).markTaken(slots(medication, monday)[0])
        assertTrue(spy.mealReady.isEmpty())
    }

    @Test fun lateRecordingDoesNotRemindAboutAMealThatIsLongPast() {
        val store = TestStore()
        val medication = beforeMeal(store)
        val spy = SpyNotifier()
        store.logger(at(monday, 12), spy).markTaken(slots(medication, monday)[0], at(monday, 7))
        assertTrue(spy.mealReady.isEmpty())
    }

    @Test fun resetCancelsTheMealReminder() {
        val store = TestStore()
        val medication = beforeMeal(store)
        val spy = SpyNotifier()
        val logger = store.logger(at(monday, 7), spy)
        val slot = slots(medication, monday)[0]
        val event = logger.markTaken(slot)
        logger.reset(DoseSlot(medication, slot.scheduledAt, event))
        assertTrue(at(monday, 7) in spy.mealCancelled)
    }

    @Test fun mealReadyTimeFollowsTheIntake() {
        val store = TestStore()
        val medication = beforeMeal(store, 30)
        val event = store.logger(at(monday, 7, 10)).markTaken(slots(medication, monday)[0])
        assertEquals(at(monday, 7, 40), DoseScheduler.mealReadyTime(event, zone))
    }
}

/** MedicationArchiveTests, SupplyScenarioTests, AmountParserTests, DoseAmountTests (iOS). */
class SmallRulesTest {
    @Test fun restoredMedicationHasNoMissedDosesFromTheArchivedTime() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60))
        store.logger(at(monday, 8)).markTaken(slots(medication, monday)[0], at(monday, 8))
        MedicationArchive.archive(medication, at(monday, 20))
        assertTrue(slots(medication, days(1, monday)).isEmpty())
        MedicationArchive.restore(medication, at(days(3, monday), 12))
        assertFalse(medication.isArchived)
        assertTrue(slots(medication, days(1, monday)).isEmpty())
        assertTrue(slots(medication, days(2, monday)).isEmpty())
        assertEquals(DoseStatus.TAKEN, slots(medication, monday).first().event?.status)
        assertEquals(1, slots(medication, days(4, monday)).size)
    }

    @Test fun threeTabletsADayWith79AtHome() {
        val medication = TestStore().medication(times = listOf(8 * 60, 12 * 60, 18 * 60), stock = 79.0)
        assertEquals(79, medication.dosesInStock)
        val now = at(monday, 7)
        assertEquals(at(days(26, monday), 12), StockService.projectedRunOutDate(medication, now, zone))
        assertEquals(days(19, monday), StockService.reorderDate(medication, now, zone, 7))
    }

    @Test fun reorderDateIsNeverInThePast() {
        val medication = TestStore().medication(times = listOf(8 * 60), stock = 2.0)
        assertEquals(monday, StockService.reorderDate(medication, at(monday, 7), zone, 7))
    }

    @Test fun acceptsWhatPeopleType() {
        assertEquals(10.0, AmountParser.parse("10"))
        assertEquals(10.5, AmountParser.parse("10,5"))
        assertEquals(10.5, AmountParser.parse("10.5"))
        assertEquals(7.0, AmountParser.parse(" 7 "))
        assertEquals(1000.0, AmountParser.parse("1 000"))
        assertEquals(0.5, AmountParser.parse("½"))
        assertEquals(2.5, AmountParser.parse("2½"))
        assertEquals(0.0, AmountParser.parse("0"))
    }

    @Test fun rejectsHalfTypedOrInvalidInput() {
        assertNull(AmountParser.parse(""))
        assertNull(AmountParser.parse("abc"))
        assertNull(AmountParser.parse("-3"))
        assertNull(AmountParser.parse(","))
    }

    @Test fun formatsForTheField() {
        val cs = Locale.forLanguageTag("cs")
        assertEquals("10", AmountParser.format(10.0, cs))
        assertEquals("10,5", AmountParser.format(10.5, cs))
        assertEquals("10.5", AmountParser.format(10.5, Locale.ENGLISH))
        assertEquals("1000", AmountParser.format(1000.0, cs))
    }

    @Test fun takenDoseSavesItsAmount() {
        val store = TestStore()
        val medication = store.medication(name = "Letrox", times = listOf(8 * 60), stock = 10.0, unitsPerDose = 1.0)
        val event = store.logger(at(monday, 8, 5)).markTaken(slots(medication, monday)[0])
        assertEquals(1.0, event.units, 0.0)
        assertEquals(DoseUnit.TABLET, event.takenUnit)
        medication.unitsPerDose = 0.5
        assertEquals(1.0, event.takenUnits, 0.0)
    }

    @Test fun undoReturnsWhatWasTaken() {
        val store = TestStore()
        val medication = store.medication(name = "Letrox", times = listOf(8 * 60), stock = 10.0, unitsPerDose = 1.0)
        val logger = store.logger(at(monday, 8, 5))
        logger.markTaken(slots(medication, monday)[0])
        assertEquals(9.0, medication.stockCount, 0.0)
        medication.unitsPerDose = 0.5
        logger.reset(slots(medication, monday)[0])
        assertEquals(10.0, medication.stockCount, 0.0)
    }

    @Test fun olderRecordsKeepTheOldAmountWhenItChanges() {
        val store = TestStore()
        val medication = store.medication(name = "Letrox", times = listOf(8 * 60), unitsPerDose = 1.0)
        val event = store.logger(at(monday, 8, 5)).markTaken(slots(medication, monday)[0])
        event.units = 0.0
        event.unitRaw = ""
        assertEquals(1.0, event.takenUnits, 0.0)
        DoseLogger.keepAmounts(medication)
        medication.unitsPerDose = 0.5
        assertEquals(1.0, event.takenUnits, 0.0)
    }

    @Test fun everyActionIsLoggedAndSaved() {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60))
        val logger = store.logger(at(monday, 9))
        logger.markTaken(slots(medication, monday)[0], at(monday, 8))
        logger.reset(slots(medication, monday)[0])
        logger.logExtraDose(medication, at(monday, 10))
        assertEquals(listOf(ActivityKind.TAKEN, ActivityKind.RESET, ActivityKind.EXTRA_DOSE), store.activity.map { it.kind })
        assertTrue(store.activity[0].detail.startsWith("Recorded later"))
        assertEquals(1, store.deletedEvents.size)
        assertTrue(store.saves >= 3)
    }

    @Test fun preferredRecordIsTheSameOnEveryDevice() {
        val medication = Medication()
        val skipped = DoseEvent(medication = medication, status = DoseStatus.SKIPPED, updatedAt = at(monday, 8))
        val late = DoseEvent(medication = medication, status = DoseStatus.TAKEN, takenAt = at(monday, 9))
        val early = DoseEvent(medication = medication, status = DoseStatus.TAKEN, takenAt = at(monday, 8, 5))
        assertEquals(early, DoseEvent.preferred(listOf(skipped, late, early)))
        assertEquals(early, DoseEvent.preferred(listOf(early, late, skipped)))
    }
}

/** VzatoTests/RunOutWarningLogTests.swift. */
class RunOutWarningTest {
    private fun should(fire: java.time.Instant, previous: java.time.Instant?, now: java.time.Instant) =
        RunOutWarning.shouldSchedule(fire, previous, now, 7, zone)

    @Test fun firstWarningAndRescheduledPendingOnesAreAllowed() {
        assertTrue(should(at(monday, 9), null, at(monday, 7)))
        assertTrue(should(at(days(1, monday), 9), at(monday, 9), at(monday, 7)))
    }

    @Test fun aDelayedRunOutDoesNotWarnAgain() {
        assertFalse(should(at(days(1, monday), 9), at(monday, 9), at(days(1, monday), 8)))
    }

    @Test fun aRefillWarnsAgainLater() {
        assertTrue(should(at(days(30, monday), 9), at(monday, 9), at(days(2, monday), 8)))
    }
}
