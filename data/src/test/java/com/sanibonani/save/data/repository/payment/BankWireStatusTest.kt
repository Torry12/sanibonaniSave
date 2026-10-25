package com.sanibonani.save.data.repository.payment

import com.sanibonani.save.domain.model.BankTransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BankWireStatusTest {

    @Test
    fun `every status maps to its database value`() {
        assertEquals("unreconciled", BankWireStatus.of(BankTransactionStatus.UNRECONCILED))
        assertEquals("matched", BankWireStatus.of(BankTransactionStatus.RECONCILED))
        assertEquals("ignored", BankWireStatus.of(BankTransactionStatus.IGNORED))
        assertEquals("manual_fix", BankWireStatus.of(BankTransactionStatus.MANUAL_FIX))
    }

    @Test
    fun `reconciled is matched on the wire, not the enum name`() {
        assertNotEquals(
            BankTransactionStatus.RECONCILED.name.lowercase(),
            BankWireStatus.of(BankTransactionStatus.RECONCILED)
        )
    }
}
