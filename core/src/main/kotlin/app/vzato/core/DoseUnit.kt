// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

/**
 * What one "unit" of a medication is. Decides how amounts read ("2 tablets", "20 drops") and how
 * the dose stepper moves. The raw values are stored and match the iOS app.
 */
enum class DoseUnit(val raw: String) {
    TABLET("tablet"),
    CAPSULE("capsule"),
    DROP("drop"),
    MILLILITER("milliliter"),
    TEASPOON("teaspoon"),
    PUFF("puff"),
    SACHET("sachet"),
    INJECTION("injection"),
    PIECE("piece");

    /** Tablets and capsules are split; everything else is counted whole. */
    val allowsFractions: Boolean get() = this == TABLET || this == CAPSULE
    val step: Double get() = if (allowsFractions) 0.25 else 1.0
    val maximumPerDose: Double get() = if (this == DROP || this == MILLILITER) 100.0 else 20.0

    companion object {
        /** Unknown or empty values read as tablets, as on iOS. */
        fun fromRaw(raw: String?): DoseUnit = entries.firstOrNull { it.raw == raw } ?: TABLET
    }
}
