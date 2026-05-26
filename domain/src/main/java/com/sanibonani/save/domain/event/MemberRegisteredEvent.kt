package com.sanibonani.save.domain.event

import com.sanibonani.save.domain.model.Member

/**
 * Event emitted when a member is successfully registered.
 */
data class MemberRegisteredEvent(val member: Member) : DomainEvent

