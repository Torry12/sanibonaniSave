package com.sanibonani.save.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

data class NavigationItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val onClick: () -> Unit
)

@Composable
fun AdaptiveScaffold(
    windowSizeClass: WindowSizeClass,
    items: List<NavigationItem>,
    selectedRoute: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val layoutType = when {
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded -> NavigationSuiteType.NavigationDrawer
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium -> NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationBar
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            items.forEach { item ->
                item(
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                    selected = selectedRoute == item.route,
                    onClick = item.onClick
                )
            }
        },
        modifier = modifier
    ) {
        content()
    }
}
