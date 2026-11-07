package com.sanibonani.save.features.member.presentation

import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.features.member.presentation.state.MemberContextPackage
import com.sanibonani.save.service.CachedGroupContext

/**
 * A cached context is only usable once it names a member and a group that belong together.
 * During Support Mode the cache can hold the target member before every section has hydrated,
 * so an identity-matched partial context is still allowed through.
 */
internal fun CachedGroupContext.toMemberContextPackage(
    partialSupportMemberId: String? = null
): MemberContextPackage? {
    val hydratedMember = member ?: return null
    val hydratedGroup = group ?: return null
    if (hydratedGroup.id != hydratedMember.groupId) return null
    val isTargetedSupportMember = partialSupportMemberId != null &&
        hydratedMember.id == partialSupportMemberId
    if (!isFullyHydrated && !isTargetedSupportMember) return null
    return MemberContextPackage(
        member = hydratedMember,
        group = hydratedGroup,
        contributions = contributions,
        beneficiaries = beneficiaries,
        loans = loans,
        calculation = calculation ?: PaymentCalculation.EMPTY,
        notifications = notifications,
        messages = messages
    )
}
