package com.actuagt.shared.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.actuagt.shared.auth.AuthGraph
import com.actuagt.shared.auth.SessionState

// Login entry point: injects the session model, observes it, and redirects
// to the feed as soon as a session exists (restored or fresh).
object LoginScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sessionModel = rememberScreenModel { AuthGraph.sessionScreenModel() }
        val session by sessionModel.sessionState.collectAsState()
        val signingIn by sessionModel.signingIn.collectAsState()
        val errorMessage by sessionModel.errorMessage.collectAsState()

        LaunchedEffect(session) {
            if (session is SessionState.SignedIn) {
                navigator.replaceAll(FeedScreen)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "ActuaGT",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Inicia sesión para continuar")
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { sessionModel.signInWithGoogle() },
                enabled = !signingIn,
            ) {
                Text(text = "Continuar con Google")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { sessionModel.signInAnonymously() },
                enabled = !signingIn,
            ) {
                Text(text = "Entrar como invitado")
            }
            if (signingIn) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
