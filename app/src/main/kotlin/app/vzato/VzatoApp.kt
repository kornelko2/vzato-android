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

/** Holds the few app-wide objects. No dependency injection library: there are only these. */
class VzatoApp : Application() {
    val database by lazy { VzatoDatabase.open(this) }
    val settings by lazy { AppSettings(this) }
    val repository by lazy {
        VzatoRepository(database, AndroidActivityTexts.deviceName(this), AndroidActivityTexts(this), getString(R.string.me))
    }

    companion object {
        fun of(context: Context): VzatoApp = context.applicationContext as VzatoApp
    }
}
