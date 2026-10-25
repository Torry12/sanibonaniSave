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

    /** Approximate geographic centers of each province for map fallback. */
    val PROVINCE_CENTERS: Map<String, Pair<Double, Double>> = mapOf(
        "Gauteng"       to Pair(-26.2708, 28.1123),
        "Western Cape"  to Pair(-33.2273, 19.8568),
        "KwaZulu-Natal" to Pair(-28.5306, 30.8958),
        "Eastern Cape"  to Pair(-32.2968, 26.4194),
        "Limpopo"       to Pair(-23.4013, 29.4179),
        "Mpumalanga"    to Pair(-25.5653, 30.5279),
        "North West"    to Pair(-26.6639, 25.2838),
        "Free State"    to Pair(-28.4541, 26.7968),
        "Northern Cape" to Pair(-29.0467, 21.8568)
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

