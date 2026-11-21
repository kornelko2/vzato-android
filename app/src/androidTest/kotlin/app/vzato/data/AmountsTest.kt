// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package app.vzato.data

import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.vzato.core.DoseUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** VzatoTests/DoseUnitTests.swift: the same amounts read the same in every language. */
@RunWith(AndroidJUnit4::class)
class AmountsTest {
    private fun res(language: String): Resources {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return context.createConfigurationContext(config).resources
    }

    private fun amount(value: Double, unit: DoseUnit, language: String) = Amounts.text(res(language), value, unit)

    @Test fun wholeAmountsUsePluralRules() {
        assertEquals("1 tablet", amount(1.0, DoseUnit.TABLET, "en"))
        assertEquals("2 tablets", amount(2.0, DoseUnit.TABLET, "en"))
        assertEquals("20 drops", amount(20.0, DoseUnit.DROP, "en"))
        assertEquals("1 drop", amount(1.0, DoseUnit.DROP, "en"))
        assertEquals("5 ml", amount(5.0, DoseUnit.MILLILITER, "en"))
    }

    @Test fun czechSlovakHungarianPlurals() {
        assertEquals("1 tableta", amount(1.0, DoseUnit.TABLET, "cs"))
        assertEquals("2 tablety", amount(2.0, DoseUnit.TABLET, "cs"))
        assertEquals("5 tablet", amount(5.0, DoseUnit.TABLET, "cs"))
        assertEquals("20 kapek", amount(20.0, DoseUnit.DROP, "cs"))
        assertEquals("3 kvapky", amount(3.0, DoseUnit.DROP, "sk"))
        assertEquals("7 kvapiek", amount(7.0, DoseUnit.DROP, "sk"))
        assertEquals("20 csepp", amount(20.0, DoseUnit.DROP, "hu"))
    }

    @Test fun fractionsReadNaturally() {
        assertEquals("½ tablet", amount(0.5, DoseUnit.TABLET, "en"))
        assertEquals("¼ capsule", amount(0.25, DoseUnit.CAPSULE, "en"))
        assertEquals("1½ tablets", amount(1.5, DoseUnit.TABLET, "en"))
        assertEquals("2½ ml", amount(2.5, DoseUnit.MILLILITER, "en"))
        assertEquals("½ tablety", amount(0.5, DoseUnit.TABLET, "cs"))
        assertEquals("1½ tablety", amount(1.5, DoseUnit.TABLET, "sk"))
    }
}
