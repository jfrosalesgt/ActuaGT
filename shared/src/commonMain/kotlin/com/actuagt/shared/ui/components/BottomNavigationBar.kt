package com.actuagt.shared.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.actuagt.shared.ui.navigation.MainTab
import com.actuagt.shared.ui.theme.ActuaGTColors

// Main bottom navigation bar shared by all tab destinations: deep petrol
// container with an amber active pill, readable in both themes.
@Composable
fun BottomNavigationBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
) {
    val container = if (isSystemInDarkTheme()) ActuaGTColors.PetrolDeep else ActuaGTColors.Petrol
    NavigationBar(
        containerColor = container,
        contentColor = Color.White,
    ) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                label = { Text(tab.title) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ActuaGTColors.PetrolDeep,
                    selectedTextColor = Color.White,
                    indicatorColor = ActuaGTColors.Amber,
                    unselectedIconColor = Color.White.copy(alpha = 0.72f),
                    unselectedTextColor = Color.White.copy(alpha = 0.72f),
                ),
            )
        }
    }
}
