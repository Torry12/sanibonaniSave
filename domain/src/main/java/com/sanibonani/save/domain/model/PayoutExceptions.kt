package com.sanibonani.save.domain.model

/**
 * Base exception for payout-related failures.
 */
sealed class PayoutException(message: String) : Exception(message)

class InsufficientFundsException(
    val requested: Money,
    val available: Money
) : PayoutException("Insufficient funds: Requested ${requested.format()} but only ${available.format()} is available.")

class MemberNotEligibleException(
    val memberId: String,
    val reason: String
) : PayoutException("Member $memberId is not eligible for payout: $reason")

class DuplicatePayoutRequestException(
    val entityId: String,
    val isLocal: Boolean = false
) : PayoutException(
    if (isLocal) "A payout request for $entityId is already queued offline. Please wait for sync."
    else "A pending payout request already exists for this entity: $entityId"
)

class GroupSuspendedException(
    val groupId: String
) : PayoutException("Group $groupId is currently suspended by the platform. Disbursements are blocked.")

/**
 * Raised when a disbursement reaches the high-value AAL2 threshold without a verified second
 * factor. [MARKER] stays inside the message because the same gate is enforced by database
 * triggers, whose failures reach the app as plain exceptions carrying this prefix.
 */
class MfaRequiredException(
    val amount: Money
) : PayoutException("$MARKER: High-value disbursement of ${amount.format()} requires AAL2 verification.") {
    companion object {
        const val MARKER = "MFA_REQUIRED"
    }
}
