package com.sanibonani.save.features.landing.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.R
import com.sanibonani.save.ui.theme.Navy

/**
 * Marketing/landing primitives shared by [com.sanibonani.save.features.landing.LandingScreen].
 * Extracted from the screen so each file has a single responsibility.
 */

@Composable
fun GroupTypeHelpItem(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "• $title",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HowItWorksItem(step: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = Navy,
            contentColor = Color.White,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(step, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.width(16.dp))
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class FeatureDetail(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
    val iconColor: Color,
    @StringRes val benefits: List<Int>
)

@Composable
fun FeatureDetailDialog(
    detail: FeatureDetail,
    onDismiss: () -> Unit
) {
    val title = stringResource(detail.title)
    val description = stringResource(detail.description)
    val benefits = detail.benefits.map { stringResource(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_got_it), fontWeight = FontWeight.Bold)
            }
        },
        icon = {
            Surface(
                color = detail.iconColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(detail.icon, null, tint = detail.iconColor)
                }
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    benefits.forEach { benefit ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "•",
                                color = detail.iconColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = benefit,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun CompactStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(4.dp))
        com.sanibonani.save.ui.components.AutoFitText(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

@Composable
fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: (() -> Unit)? = null
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val borderGradient = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.1f else 0.5f),
            iconColor.copy(alpha = 0.2f),
            Color.Black.copy(alpha = if (isDark) 0.5f else 0.1f)
        ),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Card(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, borderGradient)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle glossy highlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
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
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = iconColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = iconColor, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun StatsShimmer() {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.3f),
        Color.LightGray.copy(alpha = 0.1f),
        Color.LightGray.copy(alpha = 0.3f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(60.dp, 24.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(40.dp, 12.dp).clip(RoundedCornerShape(2.dp)).background(brush))
        }
        Box(Modifier.width(1.dp).height(32.dp).background(Color.LightGray.copy(alpha = 0.2f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(60.dp, 24.dp).clip(RoundedCornerShape(4.dp)).background(brush))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(40.dp, 12.dp).clip(RoundedCornerShape(2.dp)).background(brush))
        }
    }
}
