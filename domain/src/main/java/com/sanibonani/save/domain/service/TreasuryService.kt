package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.model.Money

/**
 * Read-side treasury questions: what a group holds and what it must hold back.
 *
 * Moving money is not one of them. A payout is booked by the disbursement use-cases through a
 * `SECURITY DEFINER` RPC, which is the only place the ledger and the cached balance are written
 * together, so a treasury method that "scheduled" a payout would either duplicate that or, as the
 * removed `schedulePayout` did, report success while transferring nothing.
 */
interface TreasuryService {
    suspend fun getTreasuryBalance(groupId: String): Result<Money>
    suspend fun calculateRequiredReserve(groupId: String): Result<Money>
}

