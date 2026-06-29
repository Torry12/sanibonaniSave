package com.sanibonani.save.domain.event

import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Payment

interface DomainEvent

data class LedgerEntryCreatedEvent(val entry: LedgerEntry) : DomainEvent

data class PaymentProcessedEvent(val payment: Payment) : DomainEvent
