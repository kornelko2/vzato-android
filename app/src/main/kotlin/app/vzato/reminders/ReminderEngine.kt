// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.vzato.core.DoseGuard
import app.vzato.core.DoseLogger
import app.vzato.core.DoseNotifying
import app.vzato.core.DoseScheduler
import app.vzato.core.DoseSlot
import app.vzato.core.DoseSource
import app.vzato.core.Medication
import app.vzato.core.PlannedReminder
import app.vzato.core.ReminderPlanner
import app.vzato.core.RunOutWarning
import app.vzato.core.StockService
import app.vzato.core.at
import app.vzato.core.day
import app.vzato.core.minutesOfDay
import app.vzato.core.plusMinutes
import app.vzato.data.AppSettings
import app.vzato.data.Graph
import app.vzato.data.VzatoRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** What the reminders will do next, for the warnings on Today. */
data class ReminderStatus(val notificationsAllowed: Boolean, val exactAlarmsAllowed: Boolean, val nextAlarm: Instant?)

/**
 * Reminders on Android. iOS plans up to 64 notifications ahead; Android keeps one alarm: the next
 * moment something is due (a dose reminder or its follow-up, a snooze, "you can eat now", a
 * run-out warning). When it rings, [fire] posts what is due and sets the next one. The plan itself
 * comes from the core ReminderPlanner, the same as on iOS, and is rebuilt after every change.
 */
class ReminderEngine(
    private val context: Context,
    private val repository: VzatoRepository,
    private val settings: AppSettings,
) {
    private val store = ReminderStore(context)
    val notifications = Notifications(context, settings)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Low supply, meal reminders and resolved doses, for the core logic. */
    val notifier = object : DoseNotifying {
        override fun notifyLowStock(medication: Medication) {
            if (settings.lowStockNotifications) notifications.postLowStock(medication)
        }

        override fun scheduleMealReady(medicationId: UUID, name: String, scheduledAt: Instant, at: Instant, waitMinutes: Int) {
            store.meals = store.meals.filterNot { it.medicationId == medicationId && it.scheduledAt == scheduledAt } +
                MealReady(medicationId, scheduledAt, at, name, waitMinutes)
        }

        override fun cancelMealReady(medicationId: UUID, scheduledAt: Instant) {
            store.meals = store.meals.filterNot { it.medicationId == medicationId && it.scheduledAt == scheduledAt }
            notifications.cancel("meal|$medicationId|${scheduledAt.toEpochMilli()}")
        }
    }

    val exactAlarmsAllowed: Boolean
        get() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun status(): ReminderStatus = ReminderStatus(notifications.allowed, exactAlarmsAllowed, store.lastCheck)

    /** After any change: drop what no longer applies and set the alarm for the next moment. */
    suspend fun rebuild() = mutex.withLock { rebuildLocked(repository.load(), Instant.now()) }

    /** The alarm rang: post everything due since the last check, then plan the next alarm. */
    suspend fun fire() = mutex.withLock {
        notifications.createChannels(settings.doseSound, settings.mealSound)
        val now = Instant.now()
        val graph = repository.load()
        val since = store.lastCheck?.takeIf { it < now } ?: now.minusSeconds(60)
        val settingsNow = settings.reminderSettings
        val medications = graph.activeMedications.associateBy { it.id }

        // Dose reminders: the latest due attempt of each time, so a phone that slept through
        // several follow-ups shows one notification, not a pile.
        val due = DueReminders.select(plan(graph, since).reminders, since, now.plusSeconds(30))
        for (reminder in due) {
            val group = DoseGroup(reminder.dayKey, reminder.minutesOfDay, reminder.medicationIds.zip(reminder.resolvedDoseMinutes) { id, m -> Dose(id, m) })
            notifications.postDose(
                "dose|${reminder.groupKey}", group, reminder.medicationIds.mapNotNull(medications::get),
                reminder.attempt, settingsNow.repeatCount, settingsNow.snoozeMinutes,
            )
        }

        // Snoozes: shown again, then repeat like a reminder until the dose counts as missed.
        val snoozes = store.snoozes.toMutableList()
        for (snooze in snoozes.toList()) {
            if (snooze.fireAt > now.plusSeconds(30)) continue
            snoozes.remove(snooze)
            val open = openDoses(graph, snooze.group)
            if (open.isEmpty()) continue
            val group = snooze.group.copy(doses = open)
            notifications.postDose(
                "snooze|${group.groupKey}", group, open.mapNotNull { medications[it.medicationId] },
                snooze.attempt, settingsNow.repeatCount, settingsNow.snoozeMinutes,
            )
            val next = now.plusMinutes(settingsNow.snoozeMinutes.toLong())
            val missedAt = doseTime(group).plusMinutes(settingsNow.missedAfterMinutes.toLong())
            if (snooze.attempt < settingsNow.repeatCount && next <= missedAt) snoozes += Snooze(group, next, snooze.attempt + 1)
        }
        store.snoozes = snoozes

        // "You can eat now".
        val (ready, waiting) = store.meals.partition { it.readyAt <= now.plusSeconds(30) }
        ready.forEach(notifications::postMeal)
        store.meals = waiting

        // Run-out warnings.
        for ((medication, fire, runOut) in runOutWarnings(graph, since)) {
            if (fire <= now.plusSeconds(30)) notifications.postRunOut(medication, runOut, zone)
        }

        store.lastCheck = now
        rebuildLocked(graph, now)
    }

    /** Taken, Snooze or Skip from a notification. */
    suspend fun handle(action: String, tag: String, group: DoseGroup) {
        notifications.createChannels(settings.doseSound, settings.mealSound)
        val now = Instant.now()
        if (action == Notifications.ACTION_SNOOZE) {
            snooze(group, now)
            notifications.cancel(tag)
            rebuild()
            return
        }
        val notRecorded = mutableListOf<Pair<Medication, Instant>>()
        repository.edit { graph, doseStore ->
            val logger = DoseLogger(doseStore, now, notifier, zone)
            val recordedNow = mutableSetOf<UUID>()
            for (dose in group.doses) {
                val slot = slotFor(graph, group.dayKey, dose) ?: continue
                if (slot.event != null) continue
                when (action) {
                    Notifications.ACTION_TAKEN -> {
                        // Nobody can be asked on the lock screen: a dose right after another one
                        // is not recorded, and the person is told why.
                        val verdict = DoseGuard.verdict(slot, now)
                        if (dose.medicationId !in recordedNow && verdict is DoseGuard.Verdict.RecentlyTaken) {
                            notRecorded += slot.medication to verdict.at
                            continue
                        }
                        logger.markTaken(slot, source = DoseSource.NOTIFICATION)
                        recordedNow += dose.medicationId
                    }
                    Notifications.ACTION_SKIP -> logger.markSkipped(slot, DoseSource.NOTIFICATION)
                }
            }
        }
        notifications.cancel(tag)
        notRecorded.forEach { (medication, at) -> notifications.postNotRecorded(medication, at, zone) }
        rebuild()
    }

    /** Snooze from the app (Today card): only these medications, the others of the time stay. */
    suspend fun snooze(slots: List<DoseSlot>) {
        val first = slots.firstOrNull() ?: return
        val day = ReminderPlanner.dayKey(first.scheduledAt, zone)
        val group = DoseGroup(day, first.scheduledAt.minutesOfDay(zone), slots.map { Dose(it.medication.id, it.scheduledAt.minutesOfDay(zone)) })
        snooze(group, Instant.now())
        rebuild()
    }

    private fun snooze(group: DoseGroup, now: Instant) {
        val fireAt = now.plusMinutes(settings.snoozeMinutes.toLong())
        // A later snooze of the same time joins the one already waiting.
        val existing = store.snoozes.firstOrNull { it.group.groupKey == group.groupKey }
        val doses = ((existing?.group?.doses ?: emptyList()) + group.doses).distinct()
        store.snoozes = store.snoozes.filterNot { it.group.groupKey == group.groupKey } + Snooze(group.copy(doses = doses), fireAt, 0)
    }

    private fun rebuildLocked(graph: Graph, now: Instant) {
        notifications.createChannels(settings.doseSound, settings.mealSound)
        // Snoozes whose doses were all recorded meanwhile are gone.
        store.snoozes = store.snoozes.mapNotNull { s -> openDoses(graph, s.group).takeIf { it.isNotEmpty() }?.let { s.copy(group = s.group.copy(doses = it)) } }
        // Notifications on screen for doses recorded elsewhere (in the app, another action) go away.
        for ((tag, group) in notifications.shownDoseGroups()) {
            if (openDoses(graph, group).isEmpty()) notifications.cancel(tag)
        }
        val moments = mutableListOf<Instant>()
        plan(graph, now).reminders.mapTo(moments) { it.fireAt }
        store.snoozes.mapTo(moments) { it.fireAt }
        store.meals.mapTo(moments) { it.readyAt }
        runOutWarnings(graph, now).mapTo(moments) { it.second }
        val next = moments.filter { it > now }.minOrNull()
        if (store.lastCheck == null) store.lastCheck = now
        setAlarm(next)
    }

    private fun plan(graph: Graph, now: Instant) = ReminderPlanner.plan(
        slots = DoseScheduler.slots(
            graph.activeMedications,
            now.minus(settings.grace),
            now.plus(Duration.ofDays(ReminderPlanner.HORIZON_DAYS.toLong())),
            zone,
        ).filter { it.event == null && !it.isUnscheduled && !DoseScheduler.isCoveredByMovedDose(it, zone) },
        now = now,
        settings = settings.reminderSettings,
        budget = Int.MAX_VALUE,
        zone = zone,
        snoozed = store.snoozes.flatMap { s -> s.group.doses.map { "${s.group.dayKey}|${it.minutes}" to it.medicationId } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() },
    )

    /** One warning per tracked medication, `runOutWarningDays` before the run-out, at 9:00 (as on iOS). */
    private fun runOutWarnings(graph: Graph, now: Instant): List<Triple<Medication, Instant, Instant>> {
        val days = settings.runOutWarningDays
        if (days <= 0 || !settings.lowStockNotifications) return emptyList()
        return graph.activeMedications.filter { it.tracksStock }.mapNotNull { medication ->
            val runOut = StockService.projectedRunOutDate(medication, now, zone) ?: return@mapNotNull null
            val fire = runOut.day(zone).minusDays(days.toLong()).at(9 * 60, zone)
            if (fire <= now) return@mapNotNull null
            if (!RunOutWarning.shouldSchedule(fire, store.runOutFire(medication.id), now, days, zone)) return@mapNotNull null
            store.recordRunOut(medication.id, fire)
            Triple(medication, fire, runOut)
        }
    }

    private fun setAlarm(at: Instant?) {
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarms.cancel(intent)
        if (at == null) return
        val millis = at.toEpochMilli()
        if (exactAlarmsAllowed) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            // Without the "Alarms & reminders" permission Android may deliver a few minutes late.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    /** The doses of a group that still have no record. */
    private fun openDoses(graph: Graph, group: DoseGroup): List<Dose> =
        group.doses.filter { dose -> slotFor(graph, group.dayKey, dose)?.event == null && graph.medication(dose.medicationId)?.isArchived == false }

    private fun slotFor(graph: Graph, dayKey: String, dose: Dose): DoseSlot? {
        val medication = graph.medication(dose.medicationId) ?: return null
        val day = ReminderPlanner.day(dayKey) ?: return null
        val at = day.at(dose.minutes, zone)
        return DoseScheduler.slots(listOf(medication), day, zone).firstOrNull { it.scheduledAt == at && !it.isUnscheduled }
            ?: DoseSlot(medication, at, null)
    }

    private fun doseTime(group: DoseGroup): Instant =
        (ReminderPlanner.day(group.dayKey) ?: Instant.now().day(zone)).at(group.minutesOfDay, zone)
}

/** Which planned reminders to show when an alarm rings. Pure, tested on the JVM. */
object DueReminders {
    /** Reminders due in (since, until], only the latest attempt of each dose time. */
    fun select(reminders: List<PlannedReminder>, since: Instant, until: Instant): List<PlannedReminder> =
        reminders.filter { it.fireAt > since && it.fireAt <= until }
            .groupBy { it.groupKey }
            .map { (_, attempts) -> attempts.maxBy { it.attempt } }
            .sortedBy { it.fireAt }
}
