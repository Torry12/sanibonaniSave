package com.sanibonani.save.domain.service

import com.sanibonani.save.domain.architecture.policy.PolicyCommand
import com.sanibonani.save.domain.architecture.policy.PolicyDecision

/**
 * Service for orchestrating group governance, rules, and policy enforcement.
 */
interface GovernanceService {
    /**
     * Evaluates a proposed action against group and platform policies.
     */
    suspend fun evaluateAction(command: PolicyCommand): Result<PolicyDecision>
}
