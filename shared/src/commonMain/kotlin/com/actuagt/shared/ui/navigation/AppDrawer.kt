package com.actuagt.shared.ui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.actuagt.shared.auth.AppUser
import com.actuagt.shared.auth.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Menu wiring without prop-drilling: App registers its drawer once (same
// register pattern as the platform media bridges) and any header opens it.
object DrawerController {
    private var drawerState: DrawerState? = null
    private var scope: CoroutineScope? = null

    fun register(drawerState: DrawerState, scope: CoroutineScope) {
        this.drawerState = drawerState
        this.scope = scope
    }

    fun open() {
        val state = drawerState
        val owner = scope
        if (state != null && owner != null) {
            owner.launch { state.open() }
        }
    }

    fun close() {
        val state = drawerState
        val owner = scope
        if (state != null && owner != null) {
            owner.launch { state.close() }
        }
    }
}

// App menu content: who is signed in plus the way out. Guests see this
// instead of being trapped, since login only shows while signed out.
@Composable
fun AppDrawerContent(
    session: SessionState,
    onSignOut: () -> Unit,
) {
    val user: AppUser? = (session as? SessionState.SignedIn)?.user
    val identity = when {
        user == null -> "Sin sesión"
        user.isAnonymous -> "Sesión de invitado"
        else -> user.email ?: user.displayName ?: "Sesión registrada"
    }
    ModalDrawerSheet {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "ACTUA GT",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = identity,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(text = "Cerrar sesión")
        }
    }
}
