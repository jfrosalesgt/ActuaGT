package com.actuagt.shared.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// signInErrorMessage keeps the login screen honest: only real failures
// explain, an abandoned Google picker stays silent.
class SignInErrorMessageTest {

    @Test
    fun successStaysSilent() {
        assertNull(signInErrorMessage(Result.success(Unit)))
    }

    @Test
    fun cancelledGooglePickerStaysSilent() {
        assertNull(signInErrorMessage(Result.failure(GoogleSignInCancelledException())))
    }

    @Test
    fun genericFailureExplainsBriefly() {
        assertEquals(
            "No se pudo iniciar sesión. Inténtalo de nuevo.",
            signInErrorMessage(Result.failure(IllegalStateException("boom"))),
        )
    }
}
