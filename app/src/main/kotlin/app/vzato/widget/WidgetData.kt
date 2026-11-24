// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.updateAll
import app.vzato.VzatoApp
import app.vzato.core.DoseGuard
import app.vzato.core.DoseLogger
import app.vzato.core.DoseScheduler
import app.vzato.core.DoseSource
import app.vzato.core.TodayDose
import app.vzato.core.TodayEntry
import app.vzato.core.TodaySnapshot
import app.vzato.core.day
import app.vzato.core.start
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** What the widget shows, and when it has to be drawn again. */
object WidgetData {
    /** Moves on when the picture may have changed without a data change (time, day, language). */
    val tick = MutableStateFlow(0L)

    /** Draws every placed widget again with fresh data. */
    suspend fun refresh(context: Context) {
        tick.value++
        TodayWidget().updateAll(context)
    }

    /** Now first, then every moment today the picture changes (a dose becomes due or missed). */
    suspend fun entries(app: VzatoApp, now: Instant = Instant.now()): List<TodayEntry> {
        val zone = ZoneId.systemDefault()
        val graph = app.repository.load()
        val entries = TodaySnapshot.entries(graph.activeMedications, now, app.settings.grace, zone)
        // A widget has no timeline: an inexact alarm draws it again at the next change, or at
        // midnight for the new day.
        val next = entries.drop(1).firstOrNull()?.date ?: now.day(zone).plusDays(1).start(zone)
        WidgetRefresh.schedule(app, next)
        return entries
    }
}

object WidgetActions {
    private val MEDICATION = ActionParameters.Key<String>("medication")
    private val SCHEDULED = ActionParameters.Key<Long>("scheduledAt")

    fun parameters(dose: TodayDose) = actionParametersOf(
        MEDICATION to dose.slot.medication.id.toString(),
        SCHEDULED to dose.scheduledAt.toEpochMilli(),
    )

    /**
     * Taken or Skip on the dose the button was drawn for, if it still has no record. Taken only
     * while one tap is safe: the state may have changed since the widget was drawn.
     */
    suspend fun record(context: Context, parameters: ActionParameters, taken: Boolean) {
        val app = VzatoApp.of(context)
        val id = parameters[MEDICATION]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        val scheduledAt = parameters[SCHEDULED]?.let(Instant::ofEpochMilli) ?: return
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        app.repository.edit { graph, store ->
            val medication = graph.medication(id) ?: return@edit
            val slot = DoseScheduler.slots(listOf(medication), scheduledAt.day(zone), zone)
                .firstOrNull { !it.isUnscheduled && it.scheduledAt == scheduledAt }
                ?.takeIf { it.event == null } ?: return@edit
            val logger = DoseLogger(store, now, app.reminders.notifier, zone)
            if (taken) {
                if (DoseGuard.verdict(slot, now) != DoseGuard.Verdict.Ok) return@edit
                logger.markTaken(slot, source = DoseSource.WIDGET)
            } else {
                logger.markSkipped(slot, DoseSource.WIDGET)
            }
        }
        // The change redraws the widget and plans the reminders again (VzatoApp), which removes
        // the notification of this dose.
    }
}

/** Draws the widget again at a given moment (inexact, no wake-up: nobody looks at a dark screen). */
class WidgetRefresh : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        VzatoApp.of(context).scope.launch {
            try {
                WidgetData.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun schedule(context: Context, at: Instant) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            alarms.set(AlarmManager.RTC, at.toEpochMilli(), intent(context))
        }

        private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(context, WidgetRefresh::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
