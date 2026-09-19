package com.sanibonani.save.domain.model

/**
 * Thrown when a severe discrepancy is detected between high-level metadata (e.g. Group member count)
 * and detailed records (e.g. actual member list fetched from network).
 */
class DataIntegrityException(
    val category: String,
    override val message: String,
    val suggestedAction: String? = null
) : Exception(message)
