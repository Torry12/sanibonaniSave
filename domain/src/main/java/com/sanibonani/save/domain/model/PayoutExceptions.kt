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
    val entityId: String
) : PayoutException("A pending payout request already exists for this entity: $entityId")

class GroupSuspendedException(
    val groupId: String
) : PayoutException("Group $groupId is currently suspended by the platform. Disbursements are blocked.")
