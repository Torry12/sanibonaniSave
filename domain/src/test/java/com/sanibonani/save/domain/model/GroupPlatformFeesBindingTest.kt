package com.sanibonani.save.domain.model

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Guards against a regression of the duplicate-singleton bug.
 *
 * `PlatformFees` was declared in *two* packages with identical fields. Because
 * `GroupModels` lives in `com.sanibonani.save.domain.model`, its unqualified
 * `PlatformFees` reference silently resolved to the copy in this package, which
 * `PlatformConfigRepository` never wrote to. Fee-config changes therefore never
 * reached `Group.registrationFee` / `Group.platformFeeAmount`.
 *
 * This test fails to compile if a second `PlatformFees` is reintroduced here.
 */
class GroupPlatformFeesBindingTest {

    @Before
    fun resetFees() {
        com.sanibonani.save.domain.config.PlatformFees.update(
            monthlyPerMember = com.sanibonani.save.domain.model.Money("10.0"),
            registrationFee = com.sanibonani.save.domain.model.Money("700.0")
        )
    }

    private fun group(currentMembers: Int) = Group(
        id = "group-1",
        name = "Fee Group",
        adminUserId = "user-1",
        province = "Gauteng",
        city = "Soweto",
        currentMembers = currentMembers
    )

    @Test
    fun `registration fee reflects a configuration change`() {
        assertEquals(Money("700.00"), group(5).registrationFee)

        com.sanibonani.save.domain.config.PlatformFees.update(
            monthlyPerMember = Money("25.0"),
            registrationFee = Money("1200.0")
        )

        // Before the fix this still returned the hardcoded R700 default.
        assertEquals(Money("1200.00"), group(5).registrationFee)
    }

    @Test
    fun `monthly platform amount reflects a configuration change`() {
        assertEquals(Money("50.00"), group(5).platformFeeAmount)

        com.sanibonani.save.domain.config.PlatformFees.update(
            monthlyPerMember = Money("25.0"),
            registrationFee = Money("700.0")
        )

        assertEquals(Money("125.00"), group(5).platformFeeAmount)
    }

    @Test
    fun `zero fee configuration is honoured`() {
        com.sanibonani.save.domain.config.PlatformFees.update(
            monthlyPerMember = Money.ZERO,
            registrationFee = Money.ZERO
        )

        assertEquals(Money.ZERO, group(10).registrationFee)
        assertEquals(Money.ZERO, group(10).platformFeeAmount)
    }
}
