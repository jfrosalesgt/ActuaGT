package com.actuagt.shared.data

import com.actuagt.shared.media.CleanupPayload
import com.actuagt.shared.media.NeutralizedText
import com.actuagt.shared.media.toNeutralizedText
import com.actuagt.shared.model.CoherencePayload
import com.actuagt.shared.model.toCoherenceVerdict
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Callable responses arrive decoded by GitLive on-device: these converters
// pin the exact shapes the functions return so a contract change surfaces
// here instead of as a generic failure message in the UI.
class CallableResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun cleanResponseKeepsTitleAndText() {
        val parsed = json.decodeFromString<CleanupPayload>(
            """{"kind":"clean","title":"Hoyo enorme en la quinta calle","text":"Se observa un hoyo enorme."}"""
        ).toNeutralizedText()
        assertTrue(parsed is NeutralizedText.Clean)
        assertEquals("Hoyo enorme en la quinta calle", parsed.title)
        assertEquals("Se observa un hoyo enorme.", parsed.text)
    }

    @Test
    fun securityAlertResponseMapsToSecurityAlert() {
        val parsed = json.decodeFromString<CleanupPayload>(
            """{"kind":"security_alert"}"""
        ).toNeutralizedText()
        assertEquals(NeutralizedText.SecurityAlert, parsed)
    }

    @Test
    fun cleanResponseWithBlankTitleFails() {
        val failure = assertFailsWith<IllegalStateException> {
            json.decodeFromString<CleanupPayload>(
                """{"kind":"clean","title":"  ","text":"Algo"}"""
            ).toNeutralizedText()
        }
        assertEquals("Respuesta de limpieza vacía", failure.message)
    }

    @Test
    fun unknownKindFails() {
        val failure = assertFailsWith<IllegalStateException> {
            json.decodeFromString<CleanupPayload>(
                """{"kind":"weird"}"""
            ).toNeutralizedText()
        }
        assertEquals("Respuesta de limpieza desconocida", failure.message)
    }

    @Test
    fun coherenceMatchKeepsReason() {
        val verdict = json.decodeFromString<CoherencePayload>(
            """{"match":true,"reason":"La foto muestra el bache descrito."}"""
        ).toCoherenceVerdict()
        assertEquals(true, verdict.match)
        assertEquals("La foto muestra el bache descrito.", verdict.reason)
    }

    @Test
    fun coherenceMismatchWithoutReasonDefaultsToEmpty() {
        val verdict = json.decodeFromString<CoherencePayload>(
            """{"match":false,"reason":""}"""
        ).toCoherenceVerdict()
        assertEquals(false, verdict.match)
        assertEquals("", verdict.reason)
    }

    @Test
    fun coherenceWithoutMatchFails() {
        val failure = assertFailsWith<IllegalStateException> {
            json.decodeFromString<CoherencePayload>(
                """{"reason":"sin fotos"}"""
            ).toCoherenceVerdict()
        }
        assertEquals("Respuesta de verificación vacía", failure.message)
    }

    @Test
    fun ackDecodesWithAndWithoutOk() {
        assertEquals(false, json.decodeFromString<FunctionAck>("{}").ok)
        assertEquals(true, json.decodeFromString<FunctionAck>("""{"ok":true}""").ok)
    }
}
