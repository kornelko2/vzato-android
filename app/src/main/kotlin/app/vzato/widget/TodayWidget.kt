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
 * Home screen widget "Today", like the iOS one: the next dose with Taken and Skip. Placed 4 × 2 by
 * default, with large text and buttons for older people; the later doses on the right. 2 × 2 still
 * works. Both buttons only where one tap is safe (DoseGuard).
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

        /** Later doses that fit above the buttons: one at 4 × 2, more when the widget is taller. */
        val upcomingRows: Int @Composable get() = ((LocalSize.current.height.value - 130f) / 50f).toInt().coerceIn(1, 3)
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

/** Text and button sizes: the wide widget is the main one and is made to be read without glasses. */
private class Sizes(val big: Boolean) {
    val state = if (big) 15.sp else 12.sp
    val name = if (big) 24.sp else 16.sp
    val amount = if (big) 19.sp else 14.sp
    val detail = if (big) 15.sp else 12.sp
    val button = if (big) 56.dp else 36.dp
    val buttonText = if (big) 20.sp else 14.sp
    val icon = if (big) 24.dp else 16.dp
    val dot = if (big) 12 else 8
}

@Composable
private fun Content(entry: TodayEntry?) {
    val context = LocalContext.current
    val wide = TodayWidget.isWide
    val sizes = Sizes(big = wide)
    Box(
        GlanceModifier.fillMaxSize().background(Colors.background).cornerRadius(20.dp).padding(if (wide) 16.dp else 14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        val dose = entry?.featured
        if (dose == null) {
            Done(entry, context.resources, sizes)
        } else if (wide) {
            // Wide (4 × 2): the dose and the later ones on top, large buttons across the bottom.
            Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    Box(GlanceModifier.defaultWeight().fillMaxHeight()) { DoseInfo(dose, sizes) }
                    if (entry.upcoming.isNotEmpty()) {
                        Spacer(GlanceModifier.width(12.dp))
                        Box(GlanceModifier.width(1.dp).fillMaxHeight().background(Colors.divider)) {}
                        Spacer(GlanceModifier.width(12.dp))
                        Box(GlanceModifier.defaultWeight().fillMaxHeight()) { Upcoming(entry.upcoming.take(TodayWidget.upcomingRows)) }
                    }
                }
                Spacer(GlanceModifier.height(10.dp))
                Buttons(dose, sizes)
            }
        } else {
            Column(GlanceModifier.fillMaxSize()) {
                DoseInfo(dose, sizes)
                Spacer(GlanceModifier.defaultWeight())
                Buttons(dose, sizes)
            }
        }
    }
}

@Composable
private fun DoseInfo(dose: TodayDose, sizes: Sizes) {
    val context = LocalContext.current
    val medication = dose.slot.medication
    Column {
        Text(
            "${time(context, dose)} · ${stateLabel(context, dose)}".uppercase(locale(context)),
            style = TextStyle(fontSize = sizes.state, fontWeight = FontWeight.Bold, color = stateColor(dose)),
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(medication.colorHex, sizes.dot)
            Spacer(GlanceModifier.width(6.dp))
            Text(medication.name, style = TextStyle(fontSize = sizes.name, fontWeight = FontWeight.Bold, color = Colors.text), maxLines = 1)
        }
        Text(
            Amounts.text(context.resources, medication.effectiveUnitsPerDose, medication.unit),
            style = TextStyle(fontSize = sizes.amount, fontWeight = FontWeight.Bold, color = Colors.text), maxLines = 1,
        )
        if (medication.dosageText.isNotBlank()) {
            Text(medication.dosageText, style = TextStyle(fontSize = sizes.detail, color = Colors.secondary), maxLines = 1)
        }
    }
}

@Composable
private fun Buttons(dose: TodayDose, sizes: Sizes) {
    val context = LocalContext.current
    if (!dose.canTakeNow) {
        // Not due yet, or the same medication was just taken: no one-tap button.
        Row(GlanceModifier.fillMaxWidth().height(sizes.button), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                context.getString(R.string.next_at_x, time(context, dose)),
                style = TextStyle(fontSize = sizes.amount, fontWeight = FontWeight.Medium, color = Colors.secondary), maxLines = 1,
            )
        }
        return
    }
    val parameters = WidgetActions.parameters(dose)
    val taken = context.getString(R.string.taken)
    val skip = context.getString(R.string.skip)
    val radius = sizes.button / 2
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            GlanceModifier.defaultWeight().height(sizes.button).background(Colors.taken).cornerRadius(radius)
                .clickable(actionRunCallback<TakenAction>(parameters)).semantics { contentDescription = taken },
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(ImageProvider(R.drawable.ic_check), contentDescription = null, colorFilter = ColorFilter.tint(Colors.onTaken), modifier = GlanceModifier.size(sizes.icon))
            Spacer(GlanceModifier.width(6.dp))
            Text(
                taken.uppercase(locale(context)),
                style = TextStyle(fontSize = sizes.buttonText, fontWeight = FontWeight.Bold, color = Colors.onTaken), maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(if (sizes.big) 10.dp else 6.dp))
        if (sizes.big) {
            // Wide: Skip says what it does, smaller than Taken so it is not tapped by mistake.
            Row(
                GlanceModifier.height(sizes.button).background(Colors.skipBackground).cornerRadius(radius).padding(horizontal = 18.dp)
                    .clickable(actionRunCallback<SkipAction>(parameters)).semantics { contentDescription = skip },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(ImageProvider(R.drawable.ic_skip), contentDescription = null, colorFilter = ColorFilter.tint(Colors.secondary), modifier = GlanceModifier.size(20.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text(skip, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Colors.secondary), maxLines = 1)
            }
        } else {
            // Icon only, so it fits next to Taken in the small widget.
            Box(
                GlanceModifier.size(sizes.button).background(Colors.skipBackground).cornerRadius(radius)
                    .clickable(actionRunCallback<SkipAction>(parameters)).semantics { contentDescription = skip },
                contentAlignment = Alignment.Center,
            ) {
                Image(ImageProvider(R.drawable.ic_skip), contentDescription = skip, colorFilter = ColorFilter.tint(Colors.secondary), modifier = GlanceModifier.size(18.dp))
            }
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
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Colors.secondary), maxLines = 1,
            )
            Spacer(GlanceModifier.height(6.dp))
        }
        for (dose in doses) {
            val medication = dose.slot.medication
            Row(GlanceModifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(medication.colorHex, 10)
                Spacer(GlanceModifier.width(6.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(medication.name, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Colors.text), maxLines = 1)
                    Text(
                        "${time(context, dose)} · ${Amounts.text(context.resources, medication.effectiveUnitsPerDose, medication.unit)}",
                        style = TextStyle(fontSize = 15.sp, color = if (dose.isCurrent) stateColor(dose) else Colors.secondary), maxLines = 1,
                    )
                }
                if (dose.isCurrent && dose.canTakeNow) {
                    val taken = context.getString(R.string.taken)
                    Box(
                        GlanceModifier.size(40.dp).background(Colors.taken).cornerRadius(20.dp)
                            .clickable(actionRunCallback<TakenAction>(WidgetActions.parameters(dose))).semantics { contentDescription = taken },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(ImageProvider(R.drawable.ic_check), contentDescription = taken, colorFilter = ColorFilter.tint(Colors.onTaken), modifier = GlanceModifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Done(entry: TodayEntry?, resources: Resources, sizes: Sizes) {
    val total = entry?.total ?: 0
    Column(GlanceModifier.fillMaxSize()) {
        Image(
            ImageProvider(if (total > 0) R.drawable.ic_check else R.drawable.ic_medications), contentDescription = null,
            colorFilter = ColorFilter.tint(if (total > 0) Colors.taken else Colors.secondary), modifier = GlanceModifier.size(if (sizes.big) 36.dp else 28.dp),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            resources.getString(if (total > 0) R.string.all_done_for_today else R.string.no_doses_today),
            style = TextStyle(fontSize = if (sizes.big) 22.sp else 16.sp, fontWeight = FontWeight.Bold, color = Colors.text),
        )
        if (entry != null && total > 0) {
            Text("${entry.taken} / $total", style = TextStyle(fontSize = sizes.detail, color = Colors.secondary))
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
