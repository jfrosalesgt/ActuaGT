package com.actuagt.shared.media

import kotlin.test.Test
import kotlin.test.assertEquals

// The dictation cleanup failure must tell the citizen what to do next
// instead of always showing the generic unavailable message.
class DictationErrorsTest {

    @Test
    fun expiredSessionAsksToSignInAgain() {
        assertEquals(
            "Tu sesión expiró. Ingresá de nuevo y probá dictar otra vez.",
            userMessageForCleanupFailure(
                RuntimeException("UNAUTHENTICATED: Sign-in is required.")
            ),
        )
    }

    @Test
    fun unusableAnswerAsksToDictateAgainShortly() {
        assertEquals(
            "La IA devolvió una respuesta incompleta. Probá dictar de nuevo con una frase corta.",
            userMessageForCleanupFailure(
                RuntimeException("The cleanup service returned an unusable answer.")
            ),
        )
    }

    @Test
    fun emptyLocalAnswerAsksToDictateAgainShortly() {
        assertEquals(
            "La IA devolvió una respuesta incompleta. Probá dictar de nuevo con una frase corta.",
            userMessageForCleanupFailure(
                IllegalStateException("Respuesta de limpieza vacía")
            ),
        )
    }

    @Test
    fun unknownFailureKeepsTheGenericMessage() {
        assertEquals(
            "El servicio de limpieza no está disponible. Escribí la descripción manualmente.",
            userMessageForCleanupFailure(
                RuntimeException("Unable to resolve host")
            ),
        )
    }
}
