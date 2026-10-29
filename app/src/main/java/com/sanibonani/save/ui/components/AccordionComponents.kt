package com.sanibonani.save.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AccordionHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    label: String = "",
    icon: ImageVector? = null,
    isSubHeader: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isSubHeader) 8.dp else 12.dp,
                vertical = if (isSubHeader) 8.dp else 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (isSubHeader) 20.dp else 24.dp)
                )
                Spacer(Modifier.width(12.dp))
            }

            Text(
                text = if (isSubHeader) title.uppercase() else title,
                style = if (isSubHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Black,
                color = if (isSubHeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                letterSpacing = if (isSubHeader) 1.sp else 0.sp
            )

            if (count > 0) {
                Text(
                    text = if (label.isEmpty()) count.toString() else "$count $label",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (isSubHeader) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (isSubHeader) 20.dp else 24.dp)
            )
        }
    }
}

@Composable
fun ProvinceHeaderCard(
    province: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    label: String = "groups"
) {
    AccordionHeader(
        title = province,
        count = count,
        isExpanded = isExpanded,
        onClick = onClick,
        label = label,
        isSubHeader = true
    )
}
