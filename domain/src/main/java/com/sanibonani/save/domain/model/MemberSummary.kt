package com.sanibonani.save.domain.model

import kotlinx.serialization.Serializable

/**
 * Aggregated summary of a member's personal and financial standing.
 * Used by Platform Administrators to perform audits before approving disbursements.
 */
@Serializable
data class MemberSummary(
    val member: Member,
    val behavior: MemberBehaviorTrack?,
    val recentLoans: List<Loan> = emptyList()
)
