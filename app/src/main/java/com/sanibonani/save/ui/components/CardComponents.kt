package com.sanibonani.save.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.ui.theme.*

@Composable
fun StatCard(
    title: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    trend: String? = null,
    color: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val displayColor = color ?: MaterialTheme.colorScheme.primary
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current

    val borderGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.1f else 0.5f),
            displayColor.copy(alpha = 0.2f),
            Color.Black.copy(alpha = if (isDark) 0.5f else 0.1f)
        ),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            } else Modifier),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if(isDark) 0.8f else 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(1.dp, borderGradient)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.03f else 0.25f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    modifier = Modifier.alignByBaseline()
                )
                if (trend != null) {
                    val isPositive = trend.startsWith("+")
                    val isNegative = trend.startsWith("-")
                    val trendColor = when {
                        isPositive -> SuccessGreen
                        isNegative -> ErrorRed
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Text(
                        text = trend,
                        style = MaterialTheme.typography.labelSmall,
                        color = trendColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(start = 8.dp)
                            .alignByBaseline(),
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            AutoFitText(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                color = displayColor,
                maxLines = 1,
                minFontSize = 10.sp
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        }
    }
}

@Composable
fun AutoFitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    minFontSize: androidx.compose.ui.unit.TextUnit = 12.sp
) {
    var fontStyle by remember(text) { mutableStateOf(style) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        style = fontStyle,
        color = color,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        textAlign = textAlign,
        maxLines = maxLines,
        softWrap = false,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontStyle.fontSize > minFontSize) {
                fontStyle = fontStyle.copy(fontSize = fontStyle.fontSize * 0.95)
            } else {
                readyToDraw = true
            }
        }
    )
}

@Composable
fun SectionHeaderCard(
    title: String,
    subtitle: String,
    icon: String? = null,
    emoji: String? = null,
    count: Int? = null,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val displayIcon = icon ?: emoji ?: "📂"
    val isDark = isSystemInDarkTheme()
    val displayColor = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current

    val borderGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.1f else 0.5f),
            displayColor.copy(alpha = 0.2f),
            Color.Black.copy(alpha = if (isDark) 0.5f else 0.1f)
        ),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.8f else 0.95f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderGradient),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.03f else 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(displayColor.copy(alpha = 0.2f), displayColor.copy(alpha = 0.05f))
                            ),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(displayIcon, fontSize = 22.sp)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    AutoFitText(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (count != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = count.toString(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    containerColor: Color? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val finalAccent = accentColor ?: MaterialTheme.colorScheme.primary
    val isDark = isSystemInDarkTheme()
    val defaultBg = if (isDark) GlassDarkBg else GlassLightBg
    val baseBg = containerColor ?: defaultBg

    val borderGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.2f else 0.8f),
            finalAccent.copy(alpha = if (isDark) 0.4f else 0.3f),
            finalAccent.copy(alpha = 0.1f),
            Color.Black.copy(alpha = if (isDark) 0.6f else 0.2f)
        ),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val bgGradient = Brush.linearGradient(
        colors = listOf(
            baseBg.copy(alpha = if (isDark) 0.75f else 0.95f),
            baseBg.copy(alpha = if (isDark) 0.45f else 0.65f)
        ),
        start = Offset.Zero,
        end = Offset(0f, Float.POSITIVE_INFINITY)
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.5.dp, borderGradient),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Box(
            modifier = Modifier
                .background(bgGradient)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isDark) 0.05f else 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(modifier = Modifier.padding(20.dp)) {
                content()
            }
        }
    }
}
