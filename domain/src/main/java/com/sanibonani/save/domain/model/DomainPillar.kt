package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Represents the major functional areas (pillars) of the domain.
 * Used for targeted cache invalidation and UI organization.
 */
@Serializable
@Parcelize
enum class DomainPillar : Parcelable {
    TREASURY,
    EQUITY,
    CREDIT,
    DISBURSEMENT,
    DYNAMICS,
    GLOBAL;

    val key: String get() = name.lowercase()
}
