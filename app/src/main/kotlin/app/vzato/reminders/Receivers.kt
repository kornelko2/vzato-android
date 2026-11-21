// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.vzato.VzatoApp
import kotlinx.coroutines.launch

/** Runs `block` in the app's scope and keeps the process alive until it is done (goAsync). */
private fun BroadcastReceiver.work(context: Context, block: suspend (VzatoApp) -> Unit) {
    val pending = goAsync()
    val app = VzatoApp.of(context)
    app.scope.launch {
        try {
            block(app)
        } finally {
            pending.finish()
        }
    }
}

/** The one alarm: something is due now. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = work(context) { it.reminders.fire() }
}

/** Taken, Snooze or Skip tapped in a notification. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val tag = intent.getStringExtra(Notifications.EXTRA_TAG) ?: return
        val group = DoseGroup.decode(intent.getStringExtra(Notifications.EXTRA_GROUP)) ?: return
        work(context) { it.reminders.handle(action, tag, group) }
    }
}

/**
 * Restart, a new time or time zone, an app update, the exact alarm permission granted: the alarm
 * is gone or wrong, plan again.
 */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = work(context) { it.reminders.rebuild() }
}
