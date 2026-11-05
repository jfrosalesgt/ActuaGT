package com.actuagt.app

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.actuagt.shared.auth.GoogleIdTokenProvider
import com.actuagt.shared.auth.GoogleSignInCancelledException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

// Real Google Sign-In through Credential Manager. The Web client id comes
// from google-services.json (generated as default_web_client_id); the
// account picker lets the user choose which Gmail to use.
class AndroidGoogleIdTokenProvider : GoogleIdTokenProvider {
    private var activity: ComponentActivity? = null

    fun register(activity: ComponentActivity) {
        this.activity = activity
    }

    override suspend fun fetchIdToken(): String? {
        val host = activity ?: return null
        val webClientId = host.getString(R.string.default_web_client_id)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            val result = CredentialManager.create(host).getCredential(host, request)
            GoogleIdTokenCredential.createFrom(result.credential.data).idToken
        } catch (cancelled: GetCredentialCancellationException) {
            // The user closed the account picker: silent, not an error.
            throw GoogleSignInCancelledException()
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google Sign-In unavailable", e)
            null
        }
    }

    companion object {
        private const val TAG = "ActuaGT"
    }
}
