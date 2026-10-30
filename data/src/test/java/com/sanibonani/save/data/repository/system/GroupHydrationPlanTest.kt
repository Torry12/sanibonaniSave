package com.sanibonani.save.data.repository.system

import com.sanibonani.save.domain.model.DomainPillar
import com.sanibonani.save.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupHydrationPlanTest {

    private val financialPillars = setOf(
        DomainPillar.EQUITY,
        DomainPillar.CREDIT,
        DomainPillar.TREASURY,
        DomainPillar.DISBURSEMENT,
        DomainPillar.BANK,
        DomainPillar.DYNAMICS
    )

    @Test
    fun `platform admin startup hydration covers every financial pillar`() {
        assertEquals(
            financialPillars,
            GroupHydrationPlan.startupPillars(UserRole.PLATFORM_ADMIN).toSet()
        )
    }

    @Test
    fun `startup hydration stays tailored to the role`() {
        assertEquals(
            setOf(DomainPillar.DISBURSEMENT, DomainPillar.BANK),
            GroupHydrationPlan.startupPillars(UserRole.GROUP_ADMIN).toSet()
        )
        assertEquals(
            setOf(DomainPillar.CREDIT),
            GroupHydrationPlan.startupPillars(UserRole.MEMBER).toSet()
        )
    }

    @Test
    fun `manual refresh covers every financial pillar for every role`() {
        // Pull-to-refresh is not role-tailored: EQUITY and TREASURY carry the balances and
        // contributions every dashboard shows, so no role may be left without them.
        val manual = GroupHydrationPlan.fullRefreshPillars

        assertEquals(financialPillars, manual.toSet())
        UserRole.entries.forEach { role ->
            assertTrue(
                "manual refresh must cover the $role startup pillars",
                manual.containsAll(GroupHydrationPlan.startupPillars(role))
            )
        }
        assertTrue(DomainPillar.GLOBAL !in manual)
    }
}
