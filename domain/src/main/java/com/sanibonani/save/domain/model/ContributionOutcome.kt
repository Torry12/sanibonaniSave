package com.sanibonani.save.domain.model

sealed interface ContributionOutcome {
    data class Success(val transactionId: String) : ContributionOutcome
    data class RequiresMfa(val amount: Money) : ContributionOutcome
    data class ComplianceBlocked(val reason: String) : ContributionOutcome
    data class Failure(val message: String, val throwable: Throwable? = null) : ContributionOutcome
}
