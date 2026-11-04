package com.actuagt.shared.media

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// verificationDataUrl is the seam that keeps unconfirmed photos out of
// Storage: the coherence call carries the bytes inline as a data URL.
class VerificationDataUrlTest {

    @Test
    fun dataUrlUsesJpegPrefixAndRoundTrips() {
        val jpeg = byteArrayOf(1, 2, 3, 4, 5)
        val url = verificationDataUrl(jpeg)
        assertTrue(url.startsWith("data:image/jpeg;base64,"))
        val decoded = Base64.Default.decode(url.removePrefix("data:image/jpeg;base64,"))
        assertEquals(jpeg.toList(), decoded.toList())
    }
}
