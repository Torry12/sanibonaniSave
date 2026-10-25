package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.domain.config.PlatformFees
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.PlatformConfig
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Pins how the in-memory fee config is seeded.
 *
 * The bridge exists because `Group.platformFeeAmount` and a few screens read the
 * `PlatformFees` singleton, while authoritative fee rules go through
 * `PlatformConfigRepository`. Seeding the repository must therefore move both halves,
 * and it must move every published fee — not just the two legacy ones.
 */
class PlatformConfigBridgeTest {

    private lateinit var repository: PlatformConfigRepositoryImpl

    private val published = PlatformConfig(
        monthlyMemberFee = Money("25.00"),
        registrationFee = Money("1200.00"),
        payoutFee = Money("7.50"),
        lateFeePercent = Money("12.50"),
        shortfallRecoveryFee = Money("35.00"),
        loanInterestRate = Money("18.00"),
        loanProcessingFee = Money("95.00"),
        groupSuspensionLiftingFee = Money("300.00"),
        memberSuspensionLiftingFee = Money("60.00"),
        autoSuspensionDays = 45
    )

    @Before
    fun setUp() {
        repository = PlatformConfigRepositoryImpl()
        PlatformFees.update(
            monthlyPerMember = PlatformConfig().monthlyMemberFee,
            registrationFee = PlatformConfig().registrationFee
        )
    }

    @Test
    fun `hydration publishes every fee, not just the legacy two`() {
        repository.hydrate(published)

        assertEquals(published, repository.current())
        assertEquals(published, repository.config.value)
    }

    @Test
    fun `a seeded config reaches the legacy fee singleton`() {
        repository.hydrate(published)

        assertEquals(Money("25.00"), PlatformFees.MONTHLY_MEMBER_FEE)
        assertEquals(Money("1200.00"), PlatformFees.REGISTRATION)
    }

    @Test
    fun `a zero fee is a published decision and survives hydration`() {
        repository.hydrate(published.copy(payoutFee = Money("0.00"), shortfallRecoveryFee = Money("0.00")))

        assertEquals(Money("0.00"), repository.current().payoutFee)
        assertEquals(Money("0.00"), repository.current().shortfallRecoveryFee)
    }
}
