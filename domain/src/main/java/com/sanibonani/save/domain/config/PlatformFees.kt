package com.sanibonani.save.domain.config

import com.sanibonani.save.domain.model.Money

/**
 * Legacy mirror of the two platform fee values that presentation and `Group` still read directly.
 *
 * [com.sanibonani.save.domain.model.PlatformConfig] is the authoritative shape, resolved by
 * `PlatformFeeService`. This object is written only by `PlatformConfigRepositoryImpl`, which must
 * keep both values in step; a second writer, or a reader that bypasses it, silently quotes a
 * different price than the one the platform bills.
 */
object PlatformFees {
    var MONTHLY_PER_MEMBER = Money("10.0")
    var MONTHLY_MEMBER_FEE: Money
        get() = MONTHLY_PER_MEMBER
        set(value) { MONTHLY_PER_MEMBER = value }
    var REGISTRATION = Money("700.0")

    fun update(monthlyPerMember: Money, registrationFee: Money) {
        MONTHLY_PER_MEMBER = monthlyPerMember
        REGISTRATION = registrationFee
    }
}
