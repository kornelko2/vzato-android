// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Shows tablet counts the way people say them: 1, ½, 1¼, 2½. Other values fall back to a plain
 * number. The words around the number ("tablets", plural rules) come from the app's string
 * resources, so the core only decides which form an amount takes.
 */
object DoseFormatter {
    /** How an amount reads, before the unit word is added. */
    sealed interface Form {
        /** A whole count, to be used with a plural resource ("2 tablets"). */
        data class Whole(val count: Int) : Form

        /** A fraction below one ("½ tablet"). */
        data class BelowOne(val text: String) : Form

        /** A mixed number or other amount above one ("1½ tablets"). */
        data class AboveOne(val text: String) : Form
    }

    fun form(value: Double, locale: Locale = Locale.getDefault()): Form {
        val quarters = (value * 4).roundToLong()
        val isExactQuarters = abs(value * 4 - quarters) < 0.001
        if (isExactQuarters && quarters % 4 == 0L) return Form.Whole((quarters / 4).toInt())
        val text = units(value, locale)
        return if (value < 1) Form.BelowOne(text) else Form.AboveOne(text)
    }

    fun units(value: Double, locale: Locale = Locale.getDefault()): String {
        val quarters = (value * 4).roundToLong()
        if (abs(value * 4 - quarters) >= 0.001 || quarters < 0) {
            return DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale)).format(value)
        }
        val whole = quarters / 4
        val fraction = when (quarters % 4) {
            1L -> "¼"
            2L -> "½"
            3L -> "¾"
            else -> ""
        }
        if (fraction.isEmpty()) return whole.toString()
        return if (whole == 0L) fraction else "$whole$fraction"
    }
}
