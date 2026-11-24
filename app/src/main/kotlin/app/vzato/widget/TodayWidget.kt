// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.widget

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.vzato.MainActivity
import app.vzato.R
import app.vzato.VzatoApp
import app.vzato.core.DoseStatus
import app.vzato.core.TodayDose
import app.vzato.core.TodayEntry
import app.vzato.data.Amounts
import app.vzato.ui.Palette
import app.vzato.ui.timeText
import java.util.Locale

/**
 * Home screen widget "Today", like the iOS one: the next dose with Taken and Skip, and in the wide
 * size the later doses. Both buttons only where one tap is safe (DoseGuard).
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = VzatoApp.of(context)
        val first = WidgetData.entries(app).firstOrNull()
        provideContent {
            // The session outlives one drawing (after a tap it stays for a while), and an update
            // then only recomposes: read the data again whenever it changes or a moment passes.
            val version by app.repository.version.collectAsState()
            val tick by WidgetData.tick.collectAsState()
            var entry by remember { mutableStateOf(first) }
            LaunchedEffect(version, tick) { entry = WidgetData.entries(app).firstOrNull() }
            Content(entry)
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
        val isWide: Boolean @Composable get() = LocalSize.current.width >= WIDE.width
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

private object Colors {
    val background = ColorProvider(day = Color.White, night = Color(0xFF1E1F1E))
    val text = ColorProvider(day = Color(0xFF1B1C1B), night = Color(0xFFE3E3E0))
    val secondary = ColorProvider(day = Color(0xFF5F6360), night = Color(0xFFA9ADAA))
    val divider = ColorProvider(day = Color(0x1F000000), night = Color(0x33FFFFFF))
    val skipBackground = ColorProvider(day = Color(0xFFE9ECE9), night = Color(0xFF353735))
    val taken = ColorProvider(Palette.taken)
    val onTaken = ColorProvider(Color.White)
    val overdue = ColorProvider(Palette.overdue)
    val missed = ColorProvider(Palette.missed)
}

@Composable
private fun Content(entry: TodayEntry?) {
    val context = LocalContext.current
    Box(
        GlanceModifier.fillMaxSize().background(Colors.background).cornerRadius(20.dp).padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (TodayWidget.isWide && entry != null && entry.featured != null) {
            Row(GlanceModifier.fillMaxSize()) {
                Box(GlanceModifier.defaultWeight().fillMaxHeight()) { Featured(entry) }
                if (entry.upcoming.isNotEmpty()) {
                    Spacer(GlanceModifier.width(12.dp))
                    Box(GlanceModifier.width(1.dp).fillMaxHeight().background(Colors.divider)) {}
                    Spacer(GlanceModifier.width(12.dp))
                    Box(GlanceModifier.defaultWeight().fillMaxHeight()) { Upcoming(entry.upcoming.take(3)) }
                }
            }
        } else if (entry?.featured != null) {
            Featured(entry)
        } else {
            Done(entry, context.resources)
        }
    }
}

@Composable
private fun Featured(entry: TodayEntry) {
    val context = LocalContext.current
    val dose = entry.featured ?: return
    val medication = dose.slot.medication
    Column(GlanceModifier.fillMaxSize()) {
        Text(
            "${time(context, dose)} · ${stateLabel(context, dose)}".uppercase(locale(context)),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = stateColor(dose)),
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(medication.colorHex, 8)
            Spacer(GlanceModifier.width(6.dp))
            Text(medication.name, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Colors.text), maxLines = 1)
        }
        Text(
            Amounts.text(context.resources, medication.effectiveUnitsPerDose, medication.unit),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Colors.text), maxLines = 1,
        )
        if (medication.dosageText.isNotBlank()) {
            Text(medication.dosageText, style = TextStyle(fontSize = 12.sp, color = Colors.secondary), maxLines = 1)
        }
        Spacer(GlanceModifier.defaultWeight())
        Buttons(dose)
    }
}

@Composable
private fun Buttons(dose: TodayDose) {
    val context = LocalContext.current
    if (!dose.canTakeNow) {
        // Not due yet, or the same medication was just taken: no one-tap button.
        Row(GlanceModifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                context.getString(R.string.next_at_x, time(context, dose)),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Colors.secondary), maxLines = 1,
            )
        }
        return
    }
    val parameters = WidgetActions.parameters(dose)
    val taken = context.getString(R.string.taken)
    val skip = context.getString(R.string.skip)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            GlanceModifier.defaultWeight().height(36.dp).background(Colors.taken).cornerRadius(18.dp)
                .clickable(actionRunCallback<TakenAction>(parameters)).semantics { contentDescription = taken },
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(ImageProvider(R.drawable.ic_check), contentDescription = null, colorFilter = ColorFilter.tint(Colors.onTaken), modifier = GlanceModifier.size(16.dp))
            Spacer(GlanceModifier.width(4.dp))
            Text(
                taken.uppercase(locale(context)),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Colors.onTaken), maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(6.dp))
        // Icon only, so it fits next to Taken in the small widget.
        Box(
            GlanceModifier.size(36.dp).background(Colors.skipBackground).cornerRadius(18.dp)
                .clickable(actionRunCallback<SkipAction>(parameters)).semantics { contentDescription = skip },
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_skip), contentDescription = skip, colorFilter = ColorFilter.tint(Colors.secondary), modifier = GlanceModifier.size(18.dp))
        }
    }
}

@Composable
private fun Upcoming(doses: List<TodayDose>) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize()) {
        if (doses.any { !it.isCurrent }) {
            Text(
                context.getString(R.string.later).uppercase(locale(context)),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Colors.secondary), maxLines = 1,
            )
            Spacer(GlanceModifier.height(6.dp))
        }
        for (dose in doses) {
            val medication = dose.slot.medication
            Row(GlanceModifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(medication.colorHex, 7)
                Spacer(GlanceModifier.width(6.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(medication.name, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Colors.text), maxLines = 1)
                    Text(
                        "${time(context, dose)} · ${Amounts.text(context.resources, medication.effectiveUnitsPerDose, medication.unit)}",
                        style = TextStyle(fontSize = 12.sp, color = if (dose.isCurrent) stateColor(dose) else Colors.secondary), maxLines = 1,
                    )
                }
                if (dose.isCurrent && dose.canTakeNow) {
                    val taken = context.getString(R.string.taken)
                    Box(
                        GlanceModifier.size(30.dp).background(Colors.taken).cornerRadius(15.dp)
                            .clickable(actionRunCallback<TakenAction>(WidgetActions.parameters(dose))).semantics { contentDescription = taken },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(ImageProvider(R.drawable.ic_check), contentDescription = taken, colorFilter = ColorFilter.tint(Colors.onTaken), modifier = GlanceModifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Done(entry: TodayEntry?, resources: Resources) {
    val total = entry?.total ?: 0
    Column(GlanceModifier.fillMaxSize()) {
        Image(
            ImageProvider(if (total > 0) R.drawable.ic_check else R.drawable.ic_medications), contentDescription = null,
            colorFilter = ColorFilter.tint(if (total > 0) Colors.taken else Colors.secondary), modifier = GlanceModifier.size(28.dp),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            resources.getString(if (total > 0) R.string.all_done_for_today else R.string.no_doses_today),
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Colors.text),
        )
        if (entry != null && total > 0) {
            Text("${entry.taken} / $total", style = TextStyle(fontSize = 12.sp, color = Colors.secondary))
        }
    }
}

@Composable
private fun Dot(hex: String, size: Int) {
    Box(GlanceModifier.size(size.dp).background(ColorProvider(Palette.fromHex(hex))).cornerRadius((size / 2).dp)) {}
}

private fun stateLabel(context: Context, dose: TodayDose): String = context.getString(
    when {
        dose.status == DoseStatus.MISSED -> R.string.missed
        dose.isOverdue -> R.string.overdue
        else -> R.string.next
    },
)

private fun stateColor(dose: TodayDose): ColorProvider = when {
    dose.status == DoseStatus.MISSED -> Colors.missed
    dose.isOverdue -> Colors.overdue
    else -> Colors.secondary
}

private fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

private fun time(context: Context, dose: TodayDose): String = timeText(dose.scheduledAt, locale(context))

class TakenAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        WidgetActions.record(context, parameters, taken = true)
}

class SkipAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        WidgetActions.record(context, parameters, taken = false)
}
