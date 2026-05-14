package com.sanibonani.save.domain.architecture.policy

import com.sanibonani.save.domain.architecture.FinancialGroupModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Domain-only policy routing contract for future governance engine extraction.
 *
 * This does not alter any current runtime flows; it is a safe abstraction layer.
 */
interface PolicyRouter {
    fun evaluate(command: PolicyCommand): Result<PolicyDecision>
}

interface PolicyRule {
    fun evaluate(command: PolicyCommand): PolicyDecision?
}

@Serializable
enum class PolicyCommandType {
    @SerialName("payout_request") PAYOUT_REQUEST,
    @SerialName("claim_approval") CLAIM_APPROVAL,
    @SerialName("disbursement_release") DISBURSEMENT_RELEASE,
    @SerialName("policy_change") POLICY_CHANGE,
    @SerialName("member_status_change") MEMBER_STATUS_CHANGE
}

@Serializable
data class PolicyCommand(
    @SerialName("group_id") val groupId: String,
    @SerialName("model") val model: FinancialGroupModel,
    @SerialName("command_type") val commandType: PolicyCommandType,
    @SerialName("actor_id") val actorId: String,
    @SerialName("amount") val amount: Double = 0.0,
    @SerialName("metadata") val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class PolicyDecision(
    @SerialName("allowed") val allowed: Boolean,
    @SerialName("reason") val reason: String,
    @SerialName("required_approvals") val requiredApprovals: Int = 1,
    @SerialName("obligations") val obligations: List<String> = emptyList()
)

class InMemoryPolicyRouter(
    private val rules: List<PolicyRule> = emptyList()
) : PolicyRouter {

    override fun evaluate(command: PolicyCommand): Result<PolicyDecision> = runCatching {
        val decisions = rules.mapNotNull { it.evaluate(command) }

        val deny = decisions.firstOrNull { !it.allowed }
        if (deny != null) {
            deny
        } else {
            decisions.maxByOrNull { it.requiredApprovals } ?: PolicyDecision(
                allowed = true,
                reason = "No matching policy rule; default-allow in scaffolding router.",
                requiredApprovals = 1,
                obligations = emptyList()
            )
        }
    }
}

/** Example governance rule for large disbursements. */
class LargeAmountDualApprovalRule(
    private val threshold: Double
) : PolicyRule {
    override fun evaluate(command: PolicyCommand): PolicyDecision? {
        if (command.amount < threshold) return null

        return PolicyDecision(
            allowed = true,
            reason = "Amount exceeds threshold; dual approval required.",
            requiredApprovals = 2,
            obligations = listOf("capture_audit_note", "second_approver_required")
        )
    }
}

/** Example deny rule for suspended groups. */
class SuspendedGroupBlockRule : PolicyRule {
    override fun evaluate(command: PolicyCommand): PolicyDecision? {
        val isSuspended = command.metadata["is_platform_suspended"] == "true"
        if (!isSuspended) return null

        return PolicyDecision(
            allowed = false,
            reason = "Group is platform suspended.",
            requiredApprovals = 0,
            obligations = listOf("notify_admin", "open_support_case")
        )
    }
}

