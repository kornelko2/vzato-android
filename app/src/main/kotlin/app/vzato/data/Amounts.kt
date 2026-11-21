// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.data

import android.content.res.Resources
import app.vzato.R
import app.vzato.core.DoseFormatter
import app.vzato.core.DoseUnit
import java.util.Locale

/** "2 tablets", "20 drops", "½ tablet", "1½ tablets", with the plural rules of each language. */
object Amounts {
    fun text(resources: Resources, value: Double, unit: DoseUnit): String {
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
        return when (val form = DoseFormatter.form(value, locale)) {
            is DoseFormatter.Form.Whole -> resources.getQuantityString(whole(unit), form.count, form.count)
            is DoseFormatter.Form.BelowOne -> resources.getString(belowOne(unit), form.text)
            is DoseFormatter.Form.AboveOne -> resources.getString(aboveOne(unit), form.text)
        }
    }

    private fun whole(unit: DoseUnit) = when (unit) {
        DoseUnit.TABLET -> R.plurals.n_tablets
        DoseUnit.CAPSULE -> R.plurals.n_capsules
        DoseUnit.DROP -> R.plurals.n_drops
        DoseUnit.MILLILITER -> R.plurals.n_ml
        DoseUnit.TEASPOON -> R.plurals.n_teaspoons
        DoseUnit.PUFF -> R.plurals.n_puffs
        DoseUnit.SACHET -> R.plurals.n_sachets
        DoseUnit.INJECTION -> R.plurals.n_injections
        DoseUnit.PIECE -> R.plurals.n_pieces
    }

    private fun belowOne(unit: DoseUnit) = when (unit) {
        DoseUnit.TABLET -> R.string.x_tablet
        DoseUnit.CAPSULE -> R.string.x_capsule
        DoseUnit.DROP -> R.string.x_drop
        DoseUnit.MILLILITER -> R.string.x_ml
        DoseUnit.TEASPOON -> R.string.x_teaspoon
        DoseUnit.PUFF -> R.string.x_puff
        DoseUnit.SACHET -> R.string.x_sachet
        DoseUnit.INJECTION -> R.string.x_injection
        DoseUnit.PIECE -> R.string.x_piece
    }

    private fun aboveOne(unit: DoseUnit) = when (unit) {
        DoseUnit.TABLET -> R.string.x_tablets
        DoseUnit.CAPSULE -> R.string.x_capsules
        DoseUnit.DROP -> R.string.x_drops
        DoseUnit.MILLILITER -> R.string.x_ml
        DoseUnit.TEASPOON -> R.string.x_teaspoons
        DoseUnit.PUFF -> R.string.x_puffs
        DoseUnit.SACHET -> R.string.x_sachets
        DoseUnit.INJECTION -> R.string.x_injections
        DoseUnit.PIECE -> R.string.x_pieces
    }
}
