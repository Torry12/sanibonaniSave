package com.sanibonani.save.domain.config

/**
 * South African reference data used across the domain and UI layers.
 *
 * Kept in the domain layer so that both the app module and any future modules
 * (e.g. a standalone admin module) can reference the same source of truth without
 * pulling in android-specific or UI dependencies.
 *
 * Values are immutable — reference data should never be mutated at runtime.
 */
object SaReferenceData {

    /** All nine South African provinces (official spellings). */
    val PROVINCES: List<String> = listOf(
        "Gauteng",
        "Western Cape",
        "KwaZulu-Natal",
        "Eastern Cape",
        "Limpopo",
        "Mpumalanga",
        "North West",
        "Free State",
        "Northern Cape"
    )

    /**
     * Major South African retail banks accepted by the platform.
     * Listed in order of market share (SARB 2024).
     */
    val BANKS: List<String> = listOf(
        "ABSA",
        "African Bank",
        "Capitec",
        "FNB",
        "Nedbank",
        "Postbank",
        "Standard Bank",
        "TymeBank"
    )
}

