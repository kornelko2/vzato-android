// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.vzato.R
import app.vzato.VzatoApp
import app.vzato.core.ActivityEntry
import app.vzato.core.ActivityKind
import app.vzato.core.CsvExport
import app.vzato.core.DoseSource
import app.vzato.ui.locale
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Who recorded what and when, on which device. Entries are never edited. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<ActivityEntry>?>(null) }
    LaunchedEffect(Unit) { entries = VzatoApp.of(context).repository.activity(2000) }
    val locale = locale()
    val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    val title = stringResource(R.string.export_log)
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.activity_log)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) } },
                actions = {
                    if (!entries.isNullOrEmpty()) {
                        TextButton(onClick = { share(context, CsvExport.activity(entries!!), title) }) { Text(stringResource(R.string.export)) }
                    }
                },
            )
        },
    ) { padding ->
        val list = entries ?: return@Scaffold
        if (list.isEmpty()) {
            Text(stringResource(R.string.no_activity_yet), Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            items(list, key = { it.id }) { entry ->
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(
                        stringResource(kindLabel(entry.kind)) + (entry.medicationName.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (entry.detail.isNotBlank()) Text(entry.detail, style = MaterialTheme.typography.bodySmall)
                    Text(
                        listOf(format.format(entry.date.atZone(ZoneId.systemDefault())), entry.deviceName, stringResource(sourceLabel(entry.source)))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

private fun kindLabel(kind: ActivityKind) = when (kind) {
    ActivityKind.TAKEN -> R.string.taken
    ActivityKind.SKIPPED -> R.string.skipped
    ActivityKind.RESET -> R.string.reset
    ActivityKind.EXTRA_DOSE -> R.string.extra_dose
    ActivityKind.STOCK_COUNTED -> R.string.supply_counted
    ActivityKind.SUPPLY_CHECK -> R.string.supply_check
    ActivityKind.DUPLICATE_MERGED -> R.string.duplicate_merged
}

private fun sourceLabel(source: DoseSource) = when (source) {
    DoseSource.APP -> R.string.app
    DoseSource.NOTIFICATION -> R.string.notification
    DoseSource.WIDGET -> R.string.widget
    DoseSource.HEALTH -> R.string.health
    DoseSource.RECONCILIATION -> R.string.supply_check
    DoseSource.CAREGIVER -> R.string.caregiver
}

private fun share(context: Context, text: String, title: String) {
    val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, "Vzato activity.csv")
    file.writeText("\uFEFF" + text)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            title,
        ),
    )
}
