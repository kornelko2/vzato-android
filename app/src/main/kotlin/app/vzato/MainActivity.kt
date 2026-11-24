// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        // Asks the launcher to place the widget, for screenshots and tests: --ez pin-widget true
        if (debuggable && intent.getBooleanExtra("pin-widget", false)) {
            getSystemService(android.appwidget.AppWidgetManager::class.java)?.requestPinAppWidget(
                android.content.ComponentName(this, app.vzato.widget.TodayWidgetReceiver::class.java), null, null,
            )
        }
        setContent {
            val appearance by vm.appearance.collectAsState()
            // Light or dark status bar icons follow the chosen appearance, not only the system.
            androidx.compose.runtime.LaunchedEffect(appearance) {
                val style = when (appearance) {
                    "light" -> androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    "dark" -> androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else -> androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            VzatoTheme(appearance) {
                VzatoUi(vm)
            }
        }
    }
}
