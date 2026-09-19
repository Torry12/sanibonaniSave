package com.sanibonani.save.data.repository.system

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.LoanRepayment
import com.sanibonani.save.domain.model.PendingOperation
import com.sanibonani.save.domain.repository.financial.ContributionRepository
import com.sanibonani.save.domain.repository.financial.LoanRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromString
import javax.inject.Inject

class ContributionOutboxHandler @Inject constructor(
    private val repository: ContributionRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = runCatching {
        val contribution = json.decodeFromString<Contribution>(operation.payload)
        repository.recordContribution(contribution).getOrThrow()
    }
}

class LoanRepaymentOutboxHandler @Inject constructor(
    private val repository: LoanRepository,
    private val json: Json
) : OutboxOperationHandler {
    override suspend fun handle(operation: PendingOperation): Result<Unit> = runCatching {
        val repayment = json.decodeFromString<LoanRepayment>(operation.payload)
        repository.recordRepayment(repayment).getOrThrow()
    }
}
