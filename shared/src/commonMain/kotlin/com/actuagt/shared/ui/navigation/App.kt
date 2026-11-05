package com.actuagt.shared.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import com.actuagt.shared.auth.AuthGraph
import com.actuagt.shared.ui.theme.ActuaGTTheme
import com.actuagt.shared.auth.SessionState
import com.actuagt.shared.ui.components.BottomNavigationBar
import com.actuagt.shared.ui.screens.LoginScreen
import com.actuagt.shared.ui.screens.ReportDetailScreen
import com.actuagt.shared.ui.screens.SplashScreen

// Application root: hosts the Voyager router inside the main scaffold.
// The bottom bar is only visible on the main tab destinations.
// initialReportId opens one report directly (push-notification deep link):
// immediately when a main tab is visible, otherwise LoginScreen consumes it
// after signing in.
@Composable
fun App(initialReportId: String? = null) {
    ActuaGTTheme {
        Navigator(screen = SplashScreen) { navigator ->
            val currentTab = MainTab.fromScreen(navigator.lastItem)
            val sessionModel = remember { AuthGraph.sessionScreenModel() }
            val session by sessionModel.sessionState.collectAsState()
            // After signing out from any tab, there is no session-gated
            // content left to show: fall back to the login screen.
            LaunchedEffect(session) {
                if (session is SessionState.SignedOut &&
                    navigator.lastItem != SplashScreen &&
                    navigator.lastItem != LoginScreen
                ) {
                    navigator.replaceAll(LoginScreen)
                }
            }
            LaunchedEffect(initialReportId, session) {
                val target = initialReportId
                if (target.isNullOrBlank()) return@LaunchedEffect
                val visible = navigator.lastItem
                // The same push tapped twice must not stack the detail twice.
                if (visible is ReportDetailScreen && visible.reportId == target) {
                    DeepLinkRequest.pendingReportId = null
                    return@LaunchedEffect
                }
                if (session is SessionState.SignedIn &&
                    MainTab.fromScreen(visible) != null &&
                    DeepLinkRequest.markPushed(target)
                ) {
                    navigator.push(ReportDetailScreen(target))
                } else {
                    DeepLinkRequest.pendingReportId = target
                }
            }

            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val drawerScope = rememberCoroutineScope()
            DrawerController.register(drawerState, drawerScope)
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    AppDrawerContent(
                        session = session,
                        onSignOut = {
                            DrawerController.close()
                            sessionModel.signOut()
                        },
                    )
                },
            ) {
                Scaffold(
                    bottomBar = {
                        if (currentTab != null) {
                            BottomNavigationBar(
                                selectedTab = currentTab,
                                onTabSelected = { tab -> navigator.replaceAll(tab.screen) },
                            )
                        }
                    },
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        CurrentScreen()
                    }
                }
            }
        }
    }
}
