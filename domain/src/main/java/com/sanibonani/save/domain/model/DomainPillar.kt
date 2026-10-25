package com.sanibonani.save.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the major functional areas (pillars) of the domain.
 * Used for targeted cache invalidation and UI organization.
 */
@Serializable
enum class DomainPillar {
    TREASURY,
    EQUITY,
    CREDIT,
    DISBURSEMENT,
    DYNAMICS,
    BANK,
    GLOBAL;

    val key: String get() = name.lowercase()
}
