package com.actuagt.shared.ui.screens

import com.actuagt.shared.media.NeutralizedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Verify must re-check the current title and description: the user may
// have edited them after dictation. Security topics block, cleaned text
// that differs is adopted, identical text keeps flowing untouched.
class VerifyTextResolutionTest {

    @Test
    fun securityAlertBlocks() {
        assertEquals(
            VerifyTextOutcome.BlockedSecurity,
            resolveVerifyText("Bache", "Agujero", NeutralizedText.SecurityAlert),
        )
    }

    @Test
    fun identicalTextKeepsFlowing() {
        assertEquals(
            VerifyTextOutcome.Keep,
            resolveVerifyText(
                "Hoyo enorme",
                "Bache peligroso",
                NeutralizedText.Clean("Hoyo enorme", "Bache peligroso"),
            ),
        )
    }

    @Test
    fun whitespaceDifferencesStillKeep() {
        assertEquals(
            VerifyTextOutcome.Keep,
            resolveVerifyText(
                "  Hoyo enorme  ",
                "Bache peligroso\n",
                NeutralizedText.Clean("Hoyo enorme", "Bache peligroso"),
            ),
        )
    }

    @Test
    fun rewordedTextIsAdopted() {
        val outcome = resolveVerifyText(
            "bache feo del alcalde idiota",
            "ese hoyo horrible",
            NeutralizedText.Clean("Hoyo en la calle", "Hoyo peligroso en la vía"),
        )
        assertTrue(outcome is VerifyTextOutcome.Adopt)
        assertEquals("Hoyo en la calle", outcome.title)
        assertEquals("Hoyo peligroso en la vía", outcome.text)
    }
}
