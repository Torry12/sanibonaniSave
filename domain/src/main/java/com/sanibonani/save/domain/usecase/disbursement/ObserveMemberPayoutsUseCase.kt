package com.sanibonani.save.domain.usecase.disbursement

import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.repository.disbursement.PayoutRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Payout requests raised for one member, re-emitted on every cache or server change.
 *
 * This is deliberately not [GetPendingPayoutsUseCase]: that one reads the whole platform's queue and
 * is only for the disbursements screen. A member must never be handed another member's rows.
 */
class ObserveMemberPayoutsUseCase @Inject constructor(
    private val payoutRepository: PayoutRepository
) {
    operator fun invoke(memberId: String): Flow<Result<List<PayoutRequest>>> =
        payoutRepository.observePayoutsForMember(memberId)
}
