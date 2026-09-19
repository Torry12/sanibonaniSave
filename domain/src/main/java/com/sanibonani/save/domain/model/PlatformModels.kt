@file:OptIn(ExperimentalSerializationApi::class)

package com.sanibonani.save.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.*

/**
 * Central constants for the SanibonaniSave platform.
 * Moved from :data to :domain to break circular dependency.
 *
 * ### Design Note — Global Mutable State
 * `PlatformFees` uses a mutable singleton so that runtime values fetched from
 * Supabase `platform_settings` are visible app-wide without threading the config
 * through every constructor.
 *
 * **Known limitation**: mutable global state makes unit tests order-dependent.
 * **Migration path**: use the injectable `PlatformConfigRepository` as the primary
 * source of truth (`StateFlow<PlatformConfig>`). `PlatformFees` is retained as a
 * compatibility bridge for existing read sites during rollout.
 *
 * Until that migration is complete, use [PlatformFees.update] as the **single**
 * write site; never assign to the properties directly.
 */
object PlatformFees {
    // Member-level monthly platform fee configured by platform admin.
    var MONTHLY_PER_MEMBER = Money(10.0)   // Backward-compatible alias (set from platform settings)
    var MONTHLY_MEMBER_FEE: Money
        get() = MONTHLY_PER_MEMBER
        set(value) { MONTHLY_PER_MEMBER = value }
    var REGISTRATION = Money(700.0)       // One-time R700 registration fee (Dynamic)
    var SHORTFALL_RECOVERY_FEE = Money(5.0) // Transaction fee for late catch-up

    /**
     * Single write-site for updating platform fee configuration.
     * Prefer this over direct property assignment to make mutation explicit and
     * easily searchable.
     *
     * @param monthlyPerMember Monthly fee charged per active member (can be zero).
     * @param registrationFee  One-time group registration fee.
     * @param recoveryFee      Transaction fee for covering shortfalls.
     */
    fun update(monthlyPerMember: Double, registrationFee: Double, recoveryFee: Double = 5.0) {
        MONTHLY_PER_MEMBER = Money(monthlyPerMember)
        REGISTRATION = Money(registrationFee)
        SHORTFALL_RECOVERY_FEE = Money(recoveryFee)
    }
}

@Serializable
@Parcelize
data class UserProfile(
    val id: String,
    @SerialName("full_name") val fullName: String?,
    val email: String?,
    val role: String
) : Parcelable
