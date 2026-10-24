package com.sanibonani.save.features.auth.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanibonani.save.domain.model.GroupType

@Composable
fun GroupTypeChip(
    groupTypes: List<GroupType>,
    selectedType: State<GroupType?>,
    onSelect: (GroupType?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        groupTypes.forEach { groupType ->
            GroupTypeOption(
                groupType = groupType,
                isSelected = selectedType.value == groupType,
                onSelect = { onSelect(groupType) }
            )
        }
    }
}

@Composable
private fun GroupTypeOption(
    groupType: GroupType,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val iconTint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    OutlinedButton(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            2.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Display the emoji icon from GroupType.icon directly
            Text(
                text = groupType.icon,
                style = MaterialTheme.typography.titleLarge,
                color = iconTint
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = groupType.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = iconTint
            )
        }
    }
}