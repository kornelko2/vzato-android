// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** VzatoTests/ReconciliationTests.swift. */
class ReconciliationTest {
    /** 10 pills counted Monday 00:00, two doses a day, nothing confirmed for three days. */
    private fun threeDaysUnconfirmed(): Triple<TestStore, Medication, Instant> {
        val store = TestStore()
        val medication = store.medication(times = listOf(8 * 60, 20 * 60), stock = 10.0)
        return Triple(store, medication, at(days(3, monday), 12)) // Thursday noon: 7 scheduled doses
    }

    @Test fun reportCountsScheduledAndRecordedDoses() {
        val (store, medication, now) = threeDaysUnconfirmed()
        val logger = store.logger(now)
        val mondaySlots = slots(medication, monday)
        logger.markTaken(mondaySlots[0], at(monday, 8))
        logger.markSkipped(mondaySlots[1])
        val report = ReconciliationService.report(medication, null, now, zone)
        assertEquals(7, report.scheduledDoses)
        assertEquals(1, report.recordedTaken)
        assertEquals(1, report.recordedSkipped)
        assertEquals(5, report.unaccounted)
        assertEquals(9.0, report.expectedStock, 0.0)
        assertEquals(ReconciliationReport.Verdict.NeedsCount, report.verdict)
    }

    @Test fun detectsConsistentMissingAndExtraUnits() {
        val (_, medication, now) = threeDaysUnconfirmed()
        assertEquals(ReconciliationReport.Verdict.Consistent, ReconciliationService.report(medication, 10.0, now, zone).verdict)
        assertEquals(ReconciliationReport.Verdict.UnrecordedDoses(3), ReconciliationService.report(medication, 7.0, now, zone).verdict)
        assertEquals(ReconciliationReport.Verdict.OverRecordedDoses(2), ReconciliationService.report(medication, 12.0, now, zone).verdict)
    }

    @Test fun applyBackfillsOldestUnrecordedDosesAndSetsStock() {
        val (store, medication, now) = threeDaysUnconfirmed()
        val report = ReconciliationService.report(medication, 7.0, now, zone)
        ReconciliationService.apply(report, 7.0, true, store, now, zone, SpyNotifier())
        val taken = medication.events.filter { it.status == DoseStatus.TAKEN }.sortedBy { it.scheduledAt }
        assertEquals(listOf(at(monday, 8), at(monday, 20), at(days(1, monday), 8)), taken.map { it.scheduledAt })
        assertTrue(taken.all { it.source == DoseSource.RECONCILIATION && it.takenAt == it.scheduledAt })
        assertEquals(7.0, medication.stockCount, 0.0)
        assertEquals(7.0, medication.stockAtLastCount, 0.0)
        assertEquals(now, medication.stockCountedAt)
        assertEquals(ReconciliationReport.Verdict.Consistent, ReconciliationService.report(medication, 7.0, now, zone).verdict)
        assertEquals(ActivityKind.SUPPLY_CHECK, store.activity.last().kind)
    }

    @Test fun applyWithoutBackfillOnlySetsStock() {
        val (store, medication, now) = threeDaysUnconfirmed()
        val report = ReconciliationService.report(medication, 7.0, now, zone)
        ReconciliationService.apply(report, 7.0, false, store, now, zone, SpyNotifier())
        assertTrue(medication.events.isEmpty())
        assertEquals(7.0, medication.stockCount, 0.0)
    }
}

/** VzatoTests/ReminderPlannerTests.swift. */
class ReminderPlannerTest {
    private val settings = ReminderPlanner.Settings(snoozeMinutes = 10, repeatCount = 2, missedAfterMinutes = 120)

    private fun openSlots(store: TestStore, days: Int = 14, from: LocalDate = monday) =
        DoseScheduler.slots(store.medications, from.start(zone), days(days, from).start(zone), zone).filter { it.event == null }

    private fun plan(store: TestStore, days: Int, now: Instant, budget: Int = 60, settings: ReminderPlanner.Settings = this.settings) =
        ReminderPlanner.plan(openSlots(store, days), now, settings, budget, zone)

    @Test fun medicationsAtTheSameTimeShareOneReminder() {
        val store = TestStore()
        store.medication(name = "Letrox", times = listOf(8 * 60))
        store.medication(name = "Ibalgin", times = listOf(8 * 60, 20 * 60))
        val reminders = plan(store, 1, at(monday, 7)).reminders
        val morning = reminders.filter { it.minutesOfDay == 8 * 60 && it.dayKey == "2026-11-02" }
        assertEquals(listOf(0, 1, 2), morning.map { it.attempt })
        assertTrue(morning.all { it.medicationIds.size == 2 })
        assertEquals(at(monday, 8, 10), morning[1].fireAt)
        assertEquals(at(monday, 8, 20), morning[2].fireAt)
        assertEquals("dose|2026-11-02|480|0", morning[0].identifier)
        val evening = reminders.filter { it.minutesOfDay == 20 * 60 && it.dayKey == "2026-11-02" }
        assertTrue(evening.size == 3 && evening[0].medicationIds.size == 1)
    }

    @Test fun pastReminderStillGetsItsRemainingFollowUps() {
        val store = TestStore()
        store.medication(name = "Letrox", times = listOf(8 * 60))
        val today = plan(store, 1, at(monday, 8, 12)).reminders.filter { it.dayKey == "2026-11-02" }
        assertEquals(listOf(2), today.map { it.attempt })
    }

    @Test fun followUpsNeverComeAfterTheDoseCountsAsMissed() {
        val store = TestStore()
        store.medication(name = "Letrox", times = listOf(8 * 60))
        val tight = ReminderPlanner.Settings(snoozeMinutes = 60, repeatCount = 3, missedAfterMinutes = 90)
        assertEquals(listOf(0, 1), plan(store, 1, at(monday, 7), settings = tight).reminders.filter { it.dayKey == "2026-11-02" }.map { it.attempt })
    }

    @Test fun recordedDosesAreNotReminded() {
        val store = TestStore()
        val medication = store.medication(name = "Letrox", times = listOf(8 * 60))
        store.logger(at(monday, 7, 45)).markTaken(slots(medication, monday)[0], at(monday, 7, 45))
        assertEquals("2026-11-03", plan(store, 2, at(monday, 7, 50)).reminders.first().dayKey)
    }

    @Test fun budgetCutsWholeGroupsAndMarksThePlanLimited() {
        val store = TestStore()
        store.medication(name = "Letrox", times = listOf(8 * 60, 20 * 60))
        val plan = plan(store, 14, at(monday, 7), budget = 7)
        assertEquals(6, plan.reminders.size)
        assertTrue(plan.limited)
        assertEquals(at(monday, 20), plan.plannedUntil)
        val wide = plan(store, 3, at(monday, 7))
        assertFalse(wide.limited)
        assertEquals(3 * 2 * 3, wide.reminders.size)
    }

    @Test fun snoozedGroupsAreLeftToTheSnooze() {
        val store = TestStore()
        val letrox = store.medication(name = "Letrox", times = listOf(8 * 60))
        val plan = ReminderPlanner.plan(openSlots(store, 1), at(monday, 7), settings, 60, zone, mapOf("2026-11-02|480" to setOf(letrox.id)))
        assertTrue(plan.reminders.isEmpty())
    }

    @Test fun snoozingOneMedicationKeepsTheOthersOfItsTime() {
        val store = TestStore()
        val letrox = store.medication(name = "Letrox", times = listOf(8 * 60))
        val vitamin = store.medication(name = "Vitamin D", times = listOf(8 * 60))
        val plan = ReminderPlanner.plan(openSlots(store, 1), at(monday, 7), settings, 60, zone, mapOf("2026-11-02|480" to setOf(letrox.id)))
        assertTrue(plan.reminders.isNotEmpty())
        assertTrue(plan.reminders.all { it.medicationIds == listOf(vitamin.id) })
    }

    @Test fun dayKeysRoundTrip() {
        assertEquals("2026-11-02", ReminderPlanner.dayKey(at(monday, 23, 59), zone))
        assertEquals(monday, ReminderPlanner.day("2026-11-02"))
        assertNull(ReminderPlanner.day("nonsense"))
    }

    @Test fun organizerCompartmentSharesOneReminderAtItsFirstDose() {
        val store = TestStore()
        val letrox = store.medication(name = "Letrox", times = listOf(7 * 60))
        val ibuprofen = store.medication(name = "Ibuprofen", times = listOf(8 * 60))
        val drops = store.medication(name = "Eye drops", times = listOf(8 * 60))
        drops.unit = DoseUnit.DROP
        // Stand-in for OrganizerService (phase B3): tablets before 11:00 go into "morning".
        val byCompartment: (DoseSlot) -> ReminderPlanner.OrganizerSlot? = { slot ->
            if (slot.medication.unit.allowsFractions && slot.scheduledAt.minutesOfDay(zone) < 11 * 60) {
                ReminderPlanner.OrganizerSlot("me|morning", "morning")
            } else {
                null
            }
        }
        val first = ReminderPlanner.plan(openSlots(store, 1), at(monday, 6), settings, 60, zone, organizer = byCompartment)
            .reminders.filter { it.attempt == 0 }
        assertEquals(2, first.size)
        assertTrue(first[0].minutesOfDay == 7 * 60 && first[0].compartment == "morning")
        assertEquals(listOf(letrox.id, ibuprofen.id), first[0].medicationIds)
        assertEquals(listOf(7 * 60, 8 * 60), first[0].doseMinutes)
        assertTrue(first[1].minutesOfDay == 8 * 60 && first[1].compartment == null && first[1].medicationIds == listOf(drops.id))
        val snoozed = ReminderPlanner.plan(
            openSlots(store, 1), at(monday, 6), settings, 60, zone,
            mapOf("2026-11-02|480" to setOf(ibuprofen.id)), byCompartment,
        )
        assertEquals(listOf(letrox.id), snoozed.reminders.first { it.attempt == 0 }.medicationIds)
    }
}

/** VzatoTests/RepeatScheduleTests.swift, including the daylight saving tests. */
class RepeatScheduleTest {
    private fun doseDays(medication: Medication, over: Int, from: LocalDate = monday) =
        (0 until over).filter { slots(medication, days(it, from)).isNotEmpty() }

    @Test fun everyOtherDay() {
        val medication = TestStore().medication(times = listOf(8 * 60), repeat = RepeatKind.DAYS, every = 2)
        assertEquals(listOf(0, 2, 4, 6), doseDays(medication, 7))
    }

    @Test fun everyTwoWeeksFromTheStartDay() {
        val medication = TestStore().medication(times = listOf(8 * 60), start = days(1, monday), repeat = RepeatKind.WEEKS, every = 2)
        assertEquals(listOf(1, 15, 29), doseDays(medication, 30))
    }

    @Test fun monthlyDewormingKeepsTheDayOfTheMonth() {
        val medication = TestStore().medication(times = listOf(8 * 60), start = days(5, monday), repeat = RepeatKind.MONTHS)
        val next = ScheduleRules.occurrenceDays(medication.schedules[0], monday, 3, zone)
        assertEquals(listOf(11, 12, 1), next.map { it.monthValue })
        assertEquals(listOf(7, 7, 7), next.map { it.dayOfMonth })
        assertTrue(slots(medication, days(6, monday)).isEmpty())
    }

    @Test fun monthlyFromThe31stFallsOnTheLastDayOfShortMonths() {
        val start = LocalDate.of(2027, 1, 31)
        val next = ScheduleRules.occurrenceDays(RepeatKind.MONTHS, 1, emptyList(), start.start(zone), null, start, 3, zone, 100)
        assertEquals(listOf(1, 2, 3), next.map { it.monthValue })
        assertEquals(listOf(31, 28, 31), next.map { it.dayOfMonth })
    }

    @Test fun everyThreeMonths() {
        val next = ScheduleRules.occurrenceDays(RepeatKind.MONTHS, 3, emptyList(), monday.start(zone), null, monday, 3, zone, 400)
        assertEquals(listOf(11, 2, 5), next.map { it.monthValue })
    }

    @Test fun nothingBeforeTheStartOrAfterTheEnd() {
        val next = ScheduleRules.occurrenceDays(
            RepeatKind.DAYS, 3, emptyList(), days(2, monday).start(zone), days(8, monday).start(zone), monday, 10, zone, 30,
        )
        assertEquals(listOf(days(2, monday), days(5, monday), days(8, monday)), next)
    }

    @Test fun weekdayPlansWorkAsBefore() {
        val medication = TestStore().medication(times = listOf(9 * 60), weekdays = listOf(2, 4))
        assertEquals(listOf(0, 2), doseDays(medication, 7))
        assertFalse(medication.schedules[0].isEveryDay)
    }

    @Test fun supplyRunOutCountsOnlyMonthlyDoses() {
        val medication = TestStore().medication(times = listOf(8 * 60), stock = 2.0, repeat = RepeatKind.MONTHS)
        // 2 Nov and 2 Dec use the two tablets; 2 Jan is the first dose not covered.
        assertEquals(at(LocalDate.of(2027, 1, 2), 8), StockService.projectedRunOutDate(medication, at(monday, 7), zone))
    }

    @Test fun remindersArePlannedOnDatesForIntervalPlans() {
        val store = TestStore()
        val monthly = store.medication(name = "Milbemax", times = listOf(8 * 60), repeat = RepeatKind.MONTHS)
        val everyOther = store.medication(name = "Letrox", times = listOf(9 * 60), repeat = RepeatKind.DAYS, every = 2)
        val now = at(monday, 9, 30)
        val slots = DoseScheduler.slots(listOf(monthly, everyOther), now, days(13, monday).start(zone), zone).filter { it.event == null }
        val plan = ReminderPlanner.plan(slots, now, ReminderPlanner.Settings(10, 0, 120), 60, zone)
        assertEquals(listOf("2026-11-04", "2026-11-06", "2026-11-08"), plan.reminders.map { it.dayKey }.take(3))
        assertTrue(plan.reminders.all { it.medicationIds == listOf(everyOther.id) && it.minutesOfDay == 9 * 60 && it.attempt == 0 })
        assertFalse(plan.limited)
    }

    @Test fun nextSuggestedTimeNeverDuplicates() {
        assertEquals(8 * 60, ScheduleRules.nextSuggestedMinutes(emptyList()))
        assertEquals(12 * 60, ScheduleRules.nextSuggestedMinutes(listOf(8 * 60)))
        assertEquals(23 * 60, ScheduleRules.nextSuggestedMinutes(ScheduleRules.suggestedMinutes))
        assertEquals("120,480", ScheduleRules.encode(listOf(480, 120, 480)))
        assertEquals(listOf(2, 4), ScheduleRules.weekdays("2, 4,9,x"))
    }
}

class DaylightSavingTest {
    private val autumn = LocalDate.of(2026, 10, 25)
    private val spring = LocalDate.of(2027, 3, 28)

    @Test fun everyDoseTimeHappensOnceOnBothChangeDays() {
        val medication = TestStore().medication(times = listOf(2 * 60 + 30, 8 * 60, 22 * 60), start = autumn.minusDays(30))
        for (day in listOf(autumn, spring)) {
            val slots = slots(medication, day)
            assertEquals(3, slots.size)
            assertEquals(3, slots.map { it.scheduledAt }.toSet().size)
            assertTrue(slots.all { it.scheduledAt.day(zone) == day })
        }
    }

    @Test fun remindersAcrossTheChangeKeepTheirWallClockTime() {
        val store = TestStore()
        store.medication(times = listOf(8 * 60), start = autumn.minusDays(30))
        val slots = DoseScheduler.slots(store.medications, autumn.minusDays(1).start(zone), autumn.plusDays(2).start(zone), zone)
        val plan = ReminderPlanner.plan(slots, autumn.minusDays(1).start(zone), ReminderPlanner.Settings(10, 0, 120), 60, zone)
        assertEquals(3, plan.reminders.size)
        assertTrue(plan.reminders.all { it.fireAt.atZone(zone).hour == 8 && it.minutesOfDay == 480 })
    }

    @Test fun everyOtherDayCountsCalendarDaysNotHours() {
        val medication = TestStore().medication(times = listOf(8 * 60), start = spring.minusDays(2), repeat = RepeatKind.DAYS, every = 2)
        assertEquals(1, slots(medication, spring).size)
        assertTrue(slots(medication, spring.plusDays(1)).isEmpty())
        assertEquals(1, slots(medication, spring.plusDays(2)).size)
    }
}
