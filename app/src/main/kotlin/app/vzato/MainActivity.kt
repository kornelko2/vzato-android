// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.vzato.ui.AppViewModel
import app.vzato.ui.VzatoTheme
import app.vzato.ui.VzatoUi

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable && intent.getBooleanExtra("sample-data", false)) {
            vm.settings.onboardingDone = true
            vm.populateSampleData()
        }
        // A medication due in two minutes, to see a real reminder: --ez reminder-test true
        if (debuggable && intent.getBooleanExtra("reminder-test", false)) vm.addReminderTest()
        setContent {
            VzatoTheme {
                VzatoUi(vm)
            }
        }
    }
}
