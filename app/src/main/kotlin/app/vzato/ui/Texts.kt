// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.vzato.R
import app.vzato.core.DoseUnit
import app.vzato.core.MealRelation
import app.vzato.core.Medication
import app.vzato.core.RepeatKind
import app.vzato.core.Schedule
import app.vzato.data.Amounts
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

// Formatting shared by the screens: amounts, times, dates and plans in the app's language.

@Composable
@ReadOnlyComposable
fun locale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

@Composable
@ReadOnlyComposable
fun resources(): Resources = LocalContext.current.resources

@Composable
fun amountText(value: Double, unit: DoseUnit): String = Amounts.text(resources(), value, unit)

@Composable
fun doseAmountText(medication: Medication): String = amountText(medication.effectiveUnitsPerDose, medication.unit)

fun timeText(instant: Instant, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone))

fun minutesText(minutes: Int, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(LocalTime.of(minutes / 60, minutes % 60))

fun dateText(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

/** The week in the reader's order (Monday first in Czech), as iOS weekday numbers 1 = Sunday. */
fun weekOrder(locale: Locale): List<Int> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0..6).map { first.plus(it.toLong()).value % 7 + 1 }
}

fun weekdayShort(appleWeekday: Int, locale: Locale): String {
    val day = DayOfWeek.of(if (appleWeekday == 1) 7 else appleWeekday - 1)
    return day.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
}

/** "Every day", "Mon, Wed, Fri", "Every 2 days", "Every month". */
@Composable
fun repeatText(schedule: Schedule): String = repeatText(schedule.repeatKind, schedule.repeatInterval, schedule.weekdays)

@Composable
fun repeatText(kind: RepeatKind, interval: Int, weekdays: List<Int>): String {
    val res = resources()
    val locale = locale()
    return when (kind) {
        RepeatKind.WEEKDAYS ->
            if (weekdays.toSet().size == 7) res.getString(R.string.every_day)
            else weekOrder(locale).filter { it in weekdays }.joinToString(", ") { weekdayShort(it, locale) }
        RepeatKind.DAYS -> if (interval == 1) res.getString(R.string.every_day) else res.getQuantityString(R.plurals.every_n_days, interval, interval)
        RepeatKind.WEEKS -> if (interval == 1) res.getString(R.string.every_week) else res.getQuantityString(R.plurals.every_n_weeks, interval, interval)
        RepeatKind.MONTHS -> if (interval == 1) res.getString(R.string.every_month) else res.getQuantityString(R.plurals.every_n_months, interval, interval)
    }
}

fun mealLabel(relation: MealRelation) = when (relation) {
    MealRelation.ANY -> R.string.any_time
    MealRelation.BEFORE -> R.string.before_a_meal
    MealRelation.WITH -> R.string.with_a_meal
    MealRelation.AFTER -> R.string.after_a_meal
}

fun unitLabel(unit: DoseUnit) = when (unit) {
    DoseUnit.TABLET -> R.string.tablets
    DoseUnit.CAPSULE -> R.string.capsules
    DoseUnit.DROP -> R.string.drops
    DoseUnit.MILLILITER -> R.string.millilitres
    DoseUnit.TEASPOON -> R.string.teaspoons
    DoseUnit.PUFF -> R.string.puffs
    DoseUnit.SACHET -> R.string.sachets
    DoseUnit.INJECTION -> R.string.injections
    DoseUnit.PIECE -> R.string.pieces
}
