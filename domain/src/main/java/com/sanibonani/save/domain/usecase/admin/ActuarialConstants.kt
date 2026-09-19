package com.sanibonani.save.domain.usecase.admin

/**
 * Centralized actuarial constants and industry standards for South African savings groups.
 * Provides a single source of truth for macro-economic data and regulatory requirements.
 */
object ActuarialConstants {

    // ── South African Macro-economic Data (2025/2026) ──────────────────────────
    const val SA_REPO_RATE_PCT = 8.25
    const val SA_PRIME_RATE_PCT = 11.75
    const val SA_CPI_INFLATION_PCT = 5.5
    const val SA_RISK_FREE_RATE_PCT = 8.50      // Standard for APV/NPV calculations

    // ── Burial Society Standards (FSB & ASISA) ─────────────────────────────────
    const val SA_WORKING_AGE_MORT_PER_1000 = 8.2
    const val SA_AVG_FUNERAL_COST = 30000.0
    const val BURIAL_MIN_SOLVENCY_RATIO = 1.30   // 130% target for stability
    const val BURIAL_SAFETY_LOADING_PCT = 35.0   // Loading above pure premium
    const val ADMIN_COST_PER_MEMBER_MONTHLY = 25.0
    const val CAPITAL_ADEQUACY_THRESHOLD = 0.15  // 15% of annual expected claims

    // ── General Savings & Liquidity ────────────────────────────────────────────
    const val EMERGENCY_TARGET_MONTHS = 6.0
    const val LIQUIDITY_RESERVE_MONTHS = 3.0

    // ── Risk Score Weighting ───────────────────────────────────────────────────
    const val WEIGHT_RESERVE_ADEQUACY = 0.30
    const val WEIGHT_CONTRIBUTION_SUFFICIENCY = 0.40
    const val WEIGHT_PAYMENT_RATE = 0.30
}
