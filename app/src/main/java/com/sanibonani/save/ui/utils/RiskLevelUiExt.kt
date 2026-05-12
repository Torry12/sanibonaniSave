package com.sanibonani.save.ui.utils

import androidx.compose.ui.graphics.Color
import com.sanibonani.save.domain.model.RiskLevel

/**
 * Presentation-layer extensions for [RiskLevel].
 *
 * Colour knowledge belongs in the UI layer, not the domain model.
 * Import this file in Composables that need to render risk level colours.
 */

/** Material colour representing this risk level. */
val RiskLevel.color: Color
    get() = when (this) {
        RiskLevel.LOW      -> Color(0xFF4CAF50)
        RiskLevel.MODERATE -> Color(0xFFFFC107)
        RiskLevel.HIGH     -> Color(0xFFFF9800)
        RiskLevel.CRITICAL -> Color(0xFFF44336)
    }

/**
 * Legacy hex string — use [color] in Compose; keep this for non-Compose
 * contexts (e.g. XML views, WebViews) during migration.
 */
val RiskLevel.colorHex: String
    get() = when (this) {
        RiskLevel.LOW      -> "#4CAF50"
        RiskLevel.MODERATE -> "#FFC107"
        RiskLevel.HIGH     -> "#FF9800"
        RiskLevel.CRITICAL -> "#F44336"
    }

