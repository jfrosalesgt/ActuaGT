package com.actuagt.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

// The suggestion follows the description wording; the user can always
// pick another category by hand afterwards.
class CategorySuggestionTest {

    @Test
    fun potholeDescriptionSuggestsVialidadOverNightKeyword() {
        assertEquals(
            "VIALIDAD",
            suggestCategoryCode(
                "Hoyo enorme en el deteriorado",
                "Bache enorme en el deteriorado. Peligroso en las noches.",
            ),
        )
    }

    @Test
    fun waterLeakSuggestsAgua() {
        assertEquals(
            "AGUA_Y_DRENAJES",
            suggestCategoryCode("Fuga", "Hay una fuga de agua en la tubería de la esquina"),
        )
    }

    @Test
    fun darkStreetSuggestsAlumbrado() {
        assertEquals(
            "ALUMBRADO",
            suggestCategoryCode("Sin luz", "El poste no tiene luz y todo queda a oscuras"),
        )
    }

    @Test
    fun garbageSuggestsDesechos() {
        assertEquals(
            "DESECHOS_Y_AMBIENTE",
            suggestCategoryCode("Basura", "Basura acumulada con mal olor frente a las casas"),
        )
    }

    @Test
    fun brokenParkGamesSuggestsEspacios() {
        assertEquals(
            "ESPACIOS_PUBLICOS",
            suggestCategoryCode("Juegos rotos", "Los juegos del parque están rotos y oxidados"),
        )
    }

    @Test
    fun unrelatedTextFallsBackToOtros() {
        assertEquals("OTROS", suggestCategoryCode("Algo raro", "Vi algo extraño ayer"))
    }

    @Test
    fun matchingIgnoresCaseAndAccents() {
        assertEquals(
            "ESPACIOS_PUBLICOS",
            suggestCategoryCode("ÁRBOL caído", "Un ÁRBOL bloquea la banca"),
        )
        assertEquals("VIALIDAD", suggestCategoryCode("BACHE", "HOYO"))
    }
}
