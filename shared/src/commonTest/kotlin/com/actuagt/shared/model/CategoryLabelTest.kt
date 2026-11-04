package com.actuagt.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryLabelTest {

    @Test
    fun knownCodesMapToSpanishLabels() {
        assertEquals("Vialidad", categoryLabel("VIALIDAD"))
        assertEquals("Agua y drenajes", categoryLabel("AGUA_Y_DRENAJES"))
        assertEquals("Alumbrado", categoryLabel("ALUMBRADO"))
        assertEquals("Desechos y ambiente", categoryLabel("DESECHOS_Y_AMBIENTE"))
        assertEquals("Espacios públicos", categoryLabel("ESPACIOS_PUBLICOS"))
        assertEquals("Otros", categoryLabel("OTROS"))
    }

    @Test
    fun unknownCodeFallsBackToOtros() {
        assertEquals("Otros", categoryLabel("RAREZA"))
        assertEquals("Otros", categoryLabel(""))
    }

    @Test
    fun coordinatesRoundToFiveDecimals() {
        assertEquals("14.63453", formatCoordinate(14.63453111))
        assertEquals("-90.55149", formatCoordinate(-90.55149))
    }
}
