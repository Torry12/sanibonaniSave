package com.sanibonani.save.domain.event

import com.sanibonani.save.domain.model.LedgerEntry

/**
 * Event emitted when a payment is successfully processed.
 */
data class PaymentProcessedEvent(val entry: LedgerEntry) : DomainEvent

