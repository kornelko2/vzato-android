// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.reminders

import app.vzato.core.PlannedReminder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class DueRemindersTest {
    private val id = UUID.randomUUID()
    private val eight = Instant.parse("2026-11-02T07:00:00Z")

    private fun reminder(minutes: Int, attempt: Int, fireAt: Instant) =
        PlannedReminder("2026-11-02", minutes, attempt, fireAt, listOf(id))

    @Test fun onlyRemindersInsideTheWindowAreShown() {
        val plan = listOf(reminder(480, 0, eight), reminder(480, 1, eight.plusSeconds(600)))
        assertEquals(listOf(0), DueReminders.select(plan, eight.minusSeconds(60), eight.plusSeconds(30)).map { it.attempt })
        assertEquals(emptyList<PlannedReminder>(), DueReminders.select(plan, eight, eight.plusSeconds(30)))
    }

    @Test fun aPhoneThatSleptThroughFollowUpsShowsOnlyTheLatest() {
        val plan = listOf(
            reminder(480, 0, eight), reminder(480, 1, eight.plusSeconds(600)), reminder(480, 2, eight.plusSeconds(1200)),
            reminder(540, 0, eight.plusSeconds(3600)),
        )
        val due = DueReminders.select(plan, eight.minusSeconds(60), eight.plusSeconds(1300))
        assertEquals(listOf(480 to 2), due.map { it.minutesOfDay to it.attempt })
    }

    @Test fun groupsSurviveTheirEncoding() {
        val group = DoseGroup("2026-11-02", 480, listOf(Dose(id, 480), Dose(UUID.randomUUID(), 510)))
        assertEquals(group, DoseGroup.decode(group.encode()))
        assertEquals(null, DoseGroup.decode("nonsense"))
    }
}
