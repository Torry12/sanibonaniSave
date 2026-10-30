package com.sanibonani.save.data.repository.system

import com.sanibonani.save.domain.model.DomainPillar
import com.sanibonani.save.domain.model.UserRole

/**
 * Which remote pillars a group hydration pulls.
 *
 * Startup hydration stays role-tailored so app launch does not pay for data the role never
 * sees. A user-initiated refresh pulls every pillar instead: pull-to-refresh must be able to
 * update each surface regardless of who is signed in, so it never branches on the role.
 */
internal object GroupHydrationPlan {

    val fullRefreshPillars: List<DomainPillar> = listOf(
        DomainPillar.EQUITY,
        DomainPillar.CREDIT,
        DomainPillar.TREASURY,
        DomainPillar.DISBURSEMENT,
        DomainPillar.BANK,
        DomainPillar.DYNAMICS
    )

    fun startupPillars(role: UserRole): List<DomainPillar> = when (role) {
        UserRole.PLATFORM_ADMIN -> fullRefreshPillars
        UserRole.GROUP_ADMIN -> listOf(DomainPillar.DISBURSEMENT, DomainPillar.BANK)
        UserRole.MEMBER -> listOf(DomainPillar.CREDIT)
    }
}
