// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato

import android.app.Application
import android.content.Context
import app.vzato.data.AndroidActivityTexts
import app.vzato.data.AppSettings
import app.vzato.data.VzatoDatabase
import app.vzato.data.VzatoRepository
import app.vzato.reminders.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds the few app-wide objects. No dependency injection library: there are only these. */
class VzatoApp : Application() {
    /** Work that outlives a screen: reminder rebuilds, notification actions. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { VzatoDatabase.open(this) }
    val settings by lazy { AppSettings(this) }
    val repository by lazy {
        VzatoRepository(database, AndroidActivityTexts.deviceName(this), AndroidActivityTexts(this), getString(R.string.me))
    }
    val reminders by lazy { ReminderEngine(this, repository, settings) }

    override fun onCreate() {
        super.onCreate()
        // Every change to the data plans the reminders again (and once at start).
        scope.launch { repository.version.collect { reminders.rebuild() } }
    }

    companion object {
        fun of(context: Context): VzatoApp = context.applicationContext as VzatoApp
    }
}
