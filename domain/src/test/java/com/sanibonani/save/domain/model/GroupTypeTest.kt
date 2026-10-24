package com.sanibonani.save.domain.model

import org.junit.Assert.*
import org.junit.Test

class GroupTypeTest {

    @Test
    fun groupTypeCapabilitiesAndMetadataAreCorrect() {
        // Capabilities
        assertFalse("Investment Club should not support loans", GroupType.INVESTMENT_CLUB.supportsLoans)
        assertTrue("Stokvel should support loans", GroupType.STOKVEL.supportsLoans)

        assertTrue("Burial Society should support beneficiaries", GroupType.BURIAL_SOCIETY.supportsBeneficiaries)
        assertTrue("Stokvel should support beneficiaries", GroupType.STOKVEL.supportsBeneficiaries)

        assertTrue("ROSCA should support rotation", GroupType.ROSCA.supportsRotation)
        assertFalse("Stokvel should not support rotation", GroupType.STOKVEL.supportsRotation)

        // Metadata
        assertEquals("Burial Claims", GroupType.BURIAL_SOCIETY.payoutLabel)
        assertEquals("Savings Payouts", GroupType.STOKVEL.payoutLabel)
        assertEquals("Rotation Payouts", GroupType.ROSCA.payoutLabel)
        assertEquals("Disinvestments", GroupType.INVESTMENT_CLUB.payoutLabel)

        // Type flags
        assertTrue(GroupType.BURIAL_SOCIETY.isBurialSociety)
        assertTrue(GroupType.STOKVEL.isStokvel)
        assertTrue(GroupType.ROSCA.isRosca)
        assertTrue(GroupType.INVESTMENT_CLUB.isInvestmentClub)
        assertTrue(GroupType.EMERGENCY_FUND.isEmergencyFund)
        assertTrue(GroupType.COMMUNITY_SAVINGS.isCommunitySavings)
        assertTrue(GroupType.HYBRID_SAVINGS_GROUP.isHybridSavings)
    }
}
