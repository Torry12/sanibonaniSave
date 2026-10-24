package com.sanibonani.save.domain.event

import com.sanibonani.save.domain.model.*

/**
 * Marker interface for all events emitted by the domain layer.
 */
interface DomainEvent

/** Emitted when a manual or system entry is recorded in the group ledger. */
data class LedgerEntryCreatedEvent(val entry: LedgerEntry) : DomainEvent

/** Emitted after a payment (inflow) has been successfully confirmed and recorded. */
data class PaymentProcessedEvent(val payment: Payment) : DomainEvent

/** Emitted when a group payout (outflow) changes status. */
data class PayoutProcessedEvent(val payout: PayoutRequest, val status: PayoutStatus) : DomainEvent

/** Emitted when a burial society claim status is updated. */
data class ClaimProcessedEvent(val claim: BeneficiaryPayoutClaim, val status: BeneficiaryClaimStatus) : DomainEvent

/** Emitted when loan funds are successfully disbursed to a member. */
data class LoanDisbursedEvent(val loan: Loan) : DomainEvent

/** Emitted when a loan application is approved by an administrator. */
data class LoanApprovedEvent(val loan: Loan) : DomainEvent

/** Emitted when a member's operational status (ACTIVE, SUSPENDED, etc.) changes. */
data class MemberStatusChangedEvent(val member: Member) : DomainEvent

/** Emitted when a full synchronization from remote to local cache is completed. */
data class FullSyncCompletedEvent(val userId: String) : DomainEvent

/** Emitted when a manual refresh for a specific domain pillar is requested. */
data class PillarRefreshRequestedEvent(val pillar: DomainPillar, val groupId: String?) : DomainEvent

/** Emitted when a bank authorization callback is received via deep link. */
data class BankCallbackEvent(val connectionId: String, val status: String) : DomainEvent
