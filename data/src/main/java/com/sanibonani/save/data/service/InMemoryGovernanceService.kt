package com.sanibonani.save.data.service

import com.sanibonani.save.domain.architecture.policy.PolicyCommand
import com.sanibonani.save.domain.architecture.policy.PolicyDecision
import com.sanibonani.save.domain.architecture.policy.PolicyRouter
import com.sanibonani.save.domain.service.GovernanceService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryGovernanceService @Inject constructor(
    private val policyRouter: PolicyRouter
) : GovernanceService {
    override suspend fun evaluateAction(command: PolicyCommand): Result<PolicyDecision> {
        return policyRouter.evaluate(command)
    }
}
