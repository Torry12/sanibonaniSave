package com.sanibonani.save.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.ui.theme.Navy

@Composable
fun GroupIcon(
    logoEmoji: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
    containerColor: Color = Navy.copy(alpha = 0.1f),
    contentColor: Color = Navy
) {
    // If the emoji is null, blank, or the default "🤝", we show the standard Groups icon
    val useStandardIcon = logoEmoji.isNullOrBlank() || logoEmoji == "🤝"

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        if (useStandardIcon) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = "Group",
                tint = contentColor,
                modifier = Modifier.size(size * 0.6f)
            )
        } else {
            Text(
                text = logoEmoji,
                fontSize = (size.value * 0.5).sp,
                modifier = Modifier.semantics { contentDescription = "Group logo" }
            )
        }
    }
}
