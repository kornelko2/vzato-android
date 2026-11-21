// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.vzato.R
import app.vzato.ui.medications.MedicationForm
import app.vzato.ui.medications.MedicationsScreen
import app.vzato.ui.onboarding.Onboarding
import app.vzato.ui.today.TodayScreen
import java.util.UUID

private enum class Tab(val title: Int, val icon: Int) {
    TODAY(R.string.today, R.drawable.ic_today),
    MEDICATIONS(R.string.medications, R.drawable.ic_medications),
    HISTORY(R.string.history, R.drawable.ic_history),
}

/**
 * The whole app: tabs at the bottom on phones, a rail at the side on tablets (like the iPad
 * sidebar), and the medication form on top when open.
 */
@Composable
fun VzatoUi(vm: AppViewModel) {
    val graph by vm.graph.collectAsState()
    val now by vm.now.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    // null = closed; "" = new medication; otherwise the id being edited.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var checking by rememberSaveable { mutableStateOf<String?>(null) }
    var onboardingDone by rememberSaveable { mutableStateOf(vm.settings.onboardingDone) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refreshClock() } }

    val data = graph ?: return
    // Decided once, when the data first arrives: saving the guide's medication must not end the guide.
    val showGuide by rememberSaveable { mutableStateOf(!vm.settings.onboardingDone && data.medications.isEmpty()) }
    if (showGuide && !onboardingDone) {
        Onboarding(
            onSave = { vm.saveMedication(it) },
            onFinish = {
                vm.settings.onboardingDone = true
                onboardingDone = true
            },
        )
        return
    }

    checking?.let { id ->
        val medication = data.medication(UUID.fromString(id))
        if (medication != null) {
            BackHandler { checking = null }
            app.vzato.ui.medications.SupplyCheckScreen(
                medication, vm.zone,
                onSave = { counted, record -> vm.applySupplyCheck(medication.id, counted, record); checking = null; editing = null },
                onClose = { checking = null },
            )
            return
        }
    }

    editing?.let { id ->
        val medication = id.takeIf { it.isNotEmpty() }?.let { data.medication(UUID.fromString(it)) }
        BackHandler { editing = null }
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background),
            contentAlignment = androidx.compose.ui.Alignment.TopCenter,
        ) {
        MedicationForm(
            initial = medication?.let { MedicationDraft.from(it) } ?: MedicationDraft(),
            onSave = { draft -> vm.saveMedication(draft); editing = null },
            onClose = { editing = null },
            onArchive = medication?.let { { vm.archive(it.id); editing = null } },
            onDelete = medication?.let { { vm.delete(it.id); editing = null } },
            modifier = Modifier.widthIn(max = 760.dp),
            onCheckSupply = medication?.let { { checking = it.id.toString() } },
        )
        }
        return
    }

    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val content: @Composable (Modifier) -> Unit = { modifier ->
        androidx.compose.foundation.layout.Box(modifier) {
            when (tab) {
                Tab.TODAY -> TodayScreen(vm, data, now, onAddMedication = { editing = "" })
                Tab.HISTORY -> app.vzato.ui.history.HistoryScreen(vm, data, now)
                Tab.MEDICATIONS -> MedicationsScreen(vm, data, now, onOpen = { editing = it.toString() }, onAdd = { editing = "" })
            }
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
            NavigationRail {
                Tab.entries.forEach { item ->
                    NavigationRailItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(painterResource(item.icon), null) },
                        label = { Text(stringResource(item.title)) },
                    )
                }
            }
            // A readable column, as on iPad, instead of lines across the whole screen.
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                content(Modifier.widthIn(max = 760.dp).fillMaxSize())
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(painterResource(item.icon), null) },
                            label = { Text(stringResource(item.title)) },
                        )
                    }
                }
            },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
    }
}
