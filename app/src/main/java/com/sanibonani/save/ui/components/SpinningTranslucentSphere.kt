package com.sanibonani.save.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanibonani.save.ui.theme.*

/**
 * Custom 3D Spinning Translucent Glass Sphere with Multi-layered Orbital Rings
 * and Glass Specular Reflections and buttery smooth animations.
 */
@Composable
fun SpinningTranslucentSphere(
    modifier: Modifier = Modifier,
    size: Dp = 140.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sphere_animation")

    // Continuous 360-degree rotation angles with smooth linear easing.
    // Durations use coprime-ish lengths so rings never realign into a visually static pattern.
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    // Own full-cycle track for the glass body — deriving it as a fraction of
    // outerRotation caused a visible snap (108° → 0°) on every ring wrap.
    val sphereRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sphereRotation"
    )

    // Gentle aura pulse
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    // Central Icon Bobbing
    val bobbingOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bobbingOffset"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // 1. Ambient Background Radial Aura Glow
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = glowPulse }
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentBlue.copy(alpha = 0.5f),
                        WarmSage.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.toPx() / 1.05f
                )
            )
        }

        // 2. Outer Orbital Ring (Tilted 3D Halo)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = outerRotation
                    rotationX = 68f
                    cameraDistance = 12f * density
                }
        ) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        AccentBlue.copy(alpha = 0.95f),
                        Color.White.copy(alpha = 0.98f),
                        WarmSage.copy(alpha = 0.35f),
                        Color.Transparent,
                        AccentBlue.copy(alpha = 0.95f)
                    )
                ),
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(35f, 18f), 0f)
                )
            )
        }

        // 3. Inner Counter-Spinning Orbital Ring (Y-Tilted Halo)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = innerRotation
                    rotationY = 62f
                    cameraDistance = 12f * density
                }
        ) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        WarmSage.copy(alpha = 0.98f),
                        Color.White.copy(alpha = 0.85f),
                        AccentBlue.copy(alpha = 0.25f),
                        Color.Transparent,
                        WarmSage.copy(alpha = 0.98f)
                    )
                ),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 4. Main 3D Translucent Glass Sphere
        Canvas(
            modifier = Modifier
                .size(size * 0.78f)
                .graphicsLayer {
                    rotationZ = sphereRotation
                }
        ) {
            val radius = this.size.minDimension / 2f

            // Sphere Depth - Radial Glass Gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.38f),
                        AccentBlue.copy(alpha = 0.48f),
                        NavyLight.copy(alpha = 0.72f),
                        NavyDeep.copy(alpha = 0.95f)
                    ),
                    center = Offset(radius * 0.65f, radius * 0.65f),
                    radius = radius * 1.35f
                ),
                radius = radius
            )

            // Glass Specular Highlight (Light Lens Flare Reflection at Top-Left)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.88f),
                        Color.White.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(radius * 0.55f, radius * 0.45f),
                    radius = radius * 0.55f
                ),
                radius = radius * 0.48f,
                center = Offset(radius * 0.55f, radius * 0.45f)
            )

            // Glass Outer Rim Highlight
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.88f),
                        AccentBlue.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.22f),
                        WarmSage.copy(alpha = 0.75f),
                        Color.White.copy(alpha = 0.88f)
                    )
                ),
                radius = radius,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 5. Central Floating Handshake Emblem
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationY = bobbingOffset * density
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🤝",
                fontSize = (size.value * 0.36f).sp
            )
        }
    }
}
