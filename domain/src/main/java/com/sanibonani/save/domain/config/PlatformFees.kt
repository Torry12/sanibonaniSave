package com.sanibonani.save.domain.config

import com.sanibonani.save.domain.model.Money

/**
 * Central constants for the SanibonaniSave platform.
 */
object PlatformFees {
    var MONTHLY_PER_MEMBER = Money(10.0)
    var MONTHLY_MEMBER_FEE: Money
        get() = MONTHLY_PER_MEMBER
        set(value) { MONTHLY_PER_MEMBER = value }
    var REGISTRATION = Money(700.0)
    var SHORTFALL_RECOVERY_FEE = Money(5.0)

    fun update(monthlyPerMember: Double, registrationFee: Double, recoveryFee: Double = 5.0) {
        MONTHLY_PER_MEMBER = Money(monthlyPerMember)
        REGISTRATION = Money(registrationFee)
        SHORTFALL_RECOVERY_FEE = Money(recoveryFee)
    }
}
