package com.sanibonani.save.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun QuickActionGrid(
    windowSizeClass: WindowSizeClass,
    actions: List<QuickAction>,
    modifier: Modifier = Modifier
) {
    val gridColumns = if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) 2 else 4
    val rowCount = (actions.size + gridColumns - 1) / gridColumns
    val gridHeight = (rowCount * 112).dp // Based on ActionButton height (100) + spacing (12)

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .height(gridHeight)
            .fillMaxWidth(),
        userScrollEnabled = false // Usually embedded in a parent LazyColumn
    ) {
        items(actions, key = { it.label }) { action ->
            ActionButton(
                icon = action.icon,
                label = action.label,
                onClick = action.onClick
            )
        }
    }
}
