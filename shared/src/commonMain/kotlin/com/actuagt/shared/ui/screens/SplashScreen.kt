package com.actuagt.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.actuagt.shared.ui.theme.ActuaGTColors
import kotlinx.coroutines.delay

// Brand splash shown right after the system splash: emblem, name and slogan
// on the petrol background, then hands over to the login screen.
object SplashScreen : Screen {
    private const val DISPLAY_MILLIS = 1_200L

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(Unit) {
            delay(DISPLAY_MILLIS)
            navigator.replaceAll(LoginScreen)
        }
        Box(
            modifier = Modifier.fillMaxSize().background(ActuaGTColors.PetrolDeep),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp),
            ) {
                Box(
                    modifier = Modifier.size(112.dp).clip(CircleShape)
                        .background(ActuaGTColors.Petrol),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        tint = ActuaGTColors.Amber,
                        modifier = Modifier.size(64.dp),
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "ACTUA GT",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ActuaGTColors.Paper,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reporta. Valida. Resuelve.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ActuaGTColors.Paper.copy(alpha = 0.8f),
                )
            }
        }
    }
}
