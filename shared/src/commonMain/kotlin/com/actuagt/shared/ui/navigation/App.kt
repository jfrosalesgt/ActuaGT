package com.actuagt.shared.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import com.actuagt.shared.ui.components.BottomNavigationBar
import com.actuagt.shared.ui.screens.LoginScreen

// Application root: hosts the Voyager router inside the main scaffold.
// The bottom bar is only visible on the main tab destinations.
@Composable
fun App() {
    MaterialTheme {
        Navigator(screen = LoginScreen) { navigator ->
            val currentTab = MainTab.fromScreen(navigator.lastItem)

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
