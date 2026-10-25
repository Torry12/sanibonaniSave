package com.sanibonani.save.ui.utils

import androidx.compose.ui.graphics.Color
import com.sanibonani.save.domain.model.RiskLevel
import com.sanibonani.save.ui.theme.RiskCritical
import com.sanibonani.save.ui.theme.RiskHigh
import com.sanibonani.save.ui.theme.RiskLow
import com.sanibonani.save.ui.theme.RiskModerate

/**
 * Presentation-layer extensions for [RiskLevel].
 *
 * Colour knowledge belongs in the UI layer, not the domain model.
 * Import this file in Composables that need to render risk level colours.
 */

/** Material colour representing this risk level. */
val RiskLevel.color: Color
    get() = when (this) {
        RiskLevel.LOW      -> RiskLow
        RiskLevel.MODERATE -> RiskModerate
        RiskLevel.HIGH     -> RiskHigh
        RiskLevel.CRITICAL -> RiskCritical
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

