package com.sanibonani.save.data.repository.system

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.LoanRepayment
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.PendingOperation
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.domain.repository.member.BeneficiaryClaimRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching

// Failures here stop the whole outbox queue, so name the missing key instead of throwing a bare NPE.
private fun Map<String, String?>.requireValue(key: String): String =
    this[key] ?: error("Outbox payload is missing '$key'")

class ContributionOutboxHandler @Inject constructor(
    private val repository: ContributionRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = safeRunCatching {
        val contribution = json.decodeFromString<Contribution>(operation.payload)
        repository.recordContribution(contribution).getOrThrow()
    }
}

class LoanRepaymentOutboxHandler @Inject constructor(
    private val repository: LoanRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = safeRunCatching {
        val repayment = json.decodeFromString<LoanRepayment>(operation.payload)
        repository.recordRepayment(repayment).getOrThrow()
    }
}

class PayoutRequestOutboxHandler @Inject constructor(
    private val repository: com.sanibonani.save.domain.repository.disbursement.PayoutRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = safeRunCatching {
        val payout = json.decodeFromString<PayoutRequest>(operation.payload)
        repository.requestPayout(payout).getOrThrow()
        Unit
    }
}

class DisbursementOutboxHandler @Inject constructor(
    private val repository: com.sanibonani.save.domain.repository.group.GroupRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = safeRunCatching {
        val data = json.decodeFromString<Map<String, String?>>(operation.payload)
        repository.recordDisbursement(
            groupId = data.requireValue("groupId"),
            amount = Money(data.requireValue("amount")),
            description = data.requireValue("description"),
            category = data.requireValue("category"),
            transactionId = data["transactionId"]
        ).getOrThrow()
        Unit
    }
}

class BalanceAdjustmentOutboxHandler @Inject constructor(
    private val repository: com.sanibonani.save.domain.repository.group.GroupRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = safeRunCatching {
        val data = json.decodeFromString<Map<String, String>>(operation.payload)
        repository.incrementGroupBalance(
            groupId = data.requireValue("groupId"),
            amount = Money(data.requireValue("amount"))
        ).getOrThrow()
        Unit
    }
}

class BeneficiaryClaimOutboxHandler @Inject constructor(
    private val repository: BeneficiaryClaimRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = safeRunCatching {
        val claim = json.decodeFromString<BeneficiaryPayoutClaim>(operation.payload)
        repository.submitClaim(claim).getOrThrow()
        Unit
    }
}
