// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Same cases as VzatoTests/DoseFormatterTests.swift. */
class DoseFormatterTest {
    @Test fun wholeNumbersStayPlain() {
        assertEquals("0", DoseFormatter.units(0.0))
        assertEquals("1", DoseFormatter.units(1.0))
        assertEquals("12", DoseFormatter.units(12.0))
    }

    @Test fun quartersUseFractionGlyphs() {
        assertEquals("¼", DoseFormatter.units(0.25))
        assertEquals("½", DoseFormatter.units(0.5))
        assertEquals("¾", DoseFormatter.units(0.75))
        assertEquals("1½", DoseFormatter.units(1.5))
        assertEquals("2¼", DoseFormatter.units(2.25))
        assertEquals("9¾", DoseFormatter.units(9.75))
    }

    @Test fun otherFractionsFallBackToNumbers() {
        assertEquals("1,3", DoseFormatter.units(1.3, Locale.forLanguageTag("cs")))
        assertEquals("1.3", DoseFormatter.units(1.3, Locale.US))
    }

    @Test fun formPicksTheRightWording() {
        assertEquals(DoseFormatter.Form.Whole(2), DoseFormatter.form(2.0))
        assertEquals(DoseFormatter.Form.BelowOne("½"), DoseFormatter.form(0.5))
        assertEquals(DoseFormatter.Form.AboveOne("1½"), DoseFormatter.form(1.5))
    }

    @Test fun unknownUnitsReadAsTablets() {
        assertEquals(DoseUnit.DROP, DoseUnit.fromRaw("drop"))
        assertEquals(DoseUnit.TABLET, DoseUnit.fromRaw(""))
        assertTrue(DoseUnit.CAPSULE.allowsFractions)
    }
}
