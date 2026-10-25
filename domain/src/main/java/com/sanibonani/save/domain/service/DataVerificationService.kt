package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.*

/**
 * Service to verify the integrity and accuracy of data displayed in the app.
 * Compares aggregated/cached values against underlying raw records.
 */
interface DataVerificationService {
    /**
     * Verifies member-specific data: total paid, status consistency, and calculation accuracy.
     */
    suspend fun verifyMemberData(
        member: Member,
        group: Group,
        contributions: List<Contribution>
    ): VerificationResult

    /**
     * Verifies group-level data: balance vs ledger, member count, and admin status.
     */
    suspend fun verifyGroupAdminData(
        group: Group,
        members: List<Member>,
        ledger: List<LedgerEntry>
    ): VerificationResult

    /**
     * Verifies platform-wide data: global totals vs individual group records.
     */
    suspend fun verifyPlatformAdminData(
        analytics: PlatformAnalytics,
        groups: List<Group>
    ): VerificationResult
}
