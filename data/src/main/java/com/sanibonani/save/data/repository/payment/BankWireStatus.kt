package com.sanibonani.save.data.repository.payment

import com.sanibonani.save.domain.model.BankTransactionStatus

/**
 * Database representation of a bank transaction status. `RECONCILED` is stored as
 * "matched", so a filter must never derive the wire value from the enum name.
 */
internal object BankWireStatus {
    fun of(status: BankTransactionStatus): String = when (status) {
        BankTransactionStatus.UNRECONCILED -> "unreconciled"
        BankTransactionStatus.RECONCILED -> "matched"
        BankTransactionStatus.IGNORED -> "ignored"
        BankTransactionStatus.MANUAL_FIX -> "manual_fix"
    }
}
