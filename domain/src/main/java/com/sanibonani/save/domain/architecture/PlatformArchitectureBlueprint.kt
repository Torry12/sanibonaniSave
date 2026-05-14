package com.sanibonani.save.domain.architecture

import com.sanibonani.save.domain.model.GroupType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Canonical architecture context that coding agents/services can use to generate
 * APIs, workflows, schemas, orchestration logic, and fintech integrations.
 *
 * This is additive metadata only; it does not alter existing app business logic.
 */
@Serializable
data class PlatformArchitectureBlueprint(
    @SerialName("version") val version: String,
    @SerialName("group_models") val groupModels: List<GroupModelBlueprint>,
    @SerialName("event_architecture") val eventArchitecture: EventArchitecture,
    @SerialName("treasury_architecture") val treasuryArchitecture: TreasuryArchitecture,
    @SerialName("governance_system") val governanceSystem: GovernanceSystem,
    @SerialName("risk_framework") val riskFramework: RiskFramework,
    @SerialName("payment_infrastructure") val paymentInfrastructure: PaymentInfrastructure,
    @SerialName("recommended_microservices") val recommendedMicroservices: List<MicroserviceBlueprint>,
    @SerialName("future_evolution") val futureEvolution: List<EvolutionMilestone>
)

@Serializable
enum class FinancialGroupModel {
    @SerialName("rosca") ROSCA,
    @SerialName("asca") ASCA,
    @SerialName("investment_group") INVESTMENT_GROUP,
    @SerialName("emergency_fund") EMERGENCY_FUND,
    @SerialName("burial_society") BURIAL_SOCIETY,
    @SerialName("grocery_group") GROCERY_GROUP,
    @SerialName("business_capital_group") BUSINESS_CAPITAL_GROUP,
    @SerialName("education_savings_group") EDUCATION_SAVINGS_GROUP,
    @SerialName("social_credit_system") SOCIAL_CREDIT_SYSTEM,
    @SerialName("hybrid_financial_group") HYBRID_FINANCIAL_GROUP
}

@Serializable
data class GroupModelBlueprint(
    @SerialName("model") val model: FinancialGroupModel,
    @SerialName("summary") val summary: String,
    @SerialName("mapped_group_types") val mappedGroupTypes: List<GroupType>,
    @SerialName("backend_functions") val backendFunctions: List<BackendFunction>,
    @SerialName("database_tables") val databaseTables: List<DatabaseTable>,
    @SerialName("api_surface") val apiSurface: List<ApiOperation>,
    @SerialName("workflows") val workflows: List<WorkflowBlueprint>,
    @SerialName("ai_agent_opportunities") val aiAgentOpportunities: List<AiAgentOpportunity>
)

@Serializable
data class BackendFunction(
    @SerialName("name") val name: String,
    @SerialName("trigger") val trigger: String,
    @SerialName("purpose") val purpose: String
)

@Serializable
data class DatabaseTable(
    @SerialName("name") val name: String,
    @SerialName("key_columns") val keyColumns: List<String>,
    @SerialName("notes") val notes: String
)

@Serializable
data class ApiOperation(
    @SerialName("operation_id") val operationId: String,
    @SerialName("method") val method: String,
    @SerialName("path") val path: String,
    @SerialName("purpose") val purpose: String
)

@Serializable
data class WorkflowBlueprint(
    @SerialName("name") val name: String,
    @SerialName("steps") val steps: List<String>,
    @SerialName("events") val events: List<String>
)

@Serializable
data class AiAgentOpportunity(
    @SerialName("name") val name: String,
    @SerialName("goal") val goal: String,
    @SerialName("inputs") val inputs: List<String>,
    @SerialName("outputs") val outputs: List<String>
)

@Serializable
data class EventArchitecture(
    @SerialName("event_bus") val eventBus: String,
    @SerialName("core_events") val coreEvents: List<String>,
    @SerialName("delivery_guarantee") val deliveryGuarantee: String,
    @SerialName("idempotency_strategy") val idempotencyStrategy: String
)

@Serializable
data class TreasuryArchitecture(
    @SerialName("ledger_model") val ledgerModel: String,
    @SerialName("sub_ledgers") val subLedgers: List<String>,
    @SerialName("reconciliation_frequency") val reconciliationFrequency: String,
    @SerialName("liquidity_rules") val liquidityRules: List<String>
)

@Serializable
data class GovernanceSystem(
    @SerialName("decision_models") val decisionModels: List<String>,
    @SerialName("controls") val controls: List<String>,
    @SerialName("audit_artifacts") val auditArtifacts: List<String>
)

@Serializable
data class RiskFramework(
    @SerialName("categories") val categories: List<String>,
    @SerialName("controls") val controls: List<String>,
    @SerialName("monitoring_signals") val monitoringSignals: List<String>
)

@Serializable
data class PaymentInfrastructure(
    @SerialName("rails") val rails: List<String>,
    @SerialName("settlement_modes") val settlementModes: List<String>,
    @SerialName("fraud_controls") val fraudControls: List<String>
)

@Serializable
data class MicroserviceBlueprint(
    @SerialName("name") val name: String,
    @SerialName("responsibility") val responsibility: String,
    @SerialName("owned_entities") val ownedEntities: List<String>
)

@Serializable
data class EvolutionMilestone(
    @SerialName("phase") val phase: String,
    @SerialName("objective") val objective: String,
    @SerialName("deliverables") val deliverables: List<String>
)

object PlatformArchitectureBlueprintCatalog {

    fun current(): PlatformArchitectureBlueprint = PlatformArchitectureBlueprint(
        version = "2026.05.13",
        groupModels = listOf(
            model(
                FinancialGroupModel.ROSCA,
                "Rotational payout groups with deterministic payout order policies.",
                mappedGroupTypes = listOf(GroupType.ROSCA),
                backendFunctions = listOf(
                    fn("calculate_rotation", "month_close", "Compute current and next payout slots."),
                    fn("apply_payout", "payout_approved", "Post ledger entries and member notifications.")
                ),
                tables = listOf(
                    table("groups", "id,type,rosca_rotation_method", "Stores fixed/random_draw/need_based/auction policy."),
                    table("payouts", "id,group_id,status", "Tracks payout lifecycle and settlement references.")
                ),
                apis = listOf(
                    api("rosca.getSchedule", "GET", "/groups/{id}/rosca/schedule", "Read cycle rotation schedule."),
                    api("rosca.rotate", "POST", "/groups/{id}/rosca/rotate", "Advance cycle and nominate recipient.")
                ),
                workflows = listOf(
                    flow("Monthly rotation", listOf("collect contributions", "validate eligible members", "select recipient", "issue payout"), listOf("contribution.posted", "rosca.recipient.selected", "payout.requested"))
                ),
                ai = listOf(
                    ai("Defaulter pressure predictor", "Estimate dropout risk mid-cycle.", listOf("payment history", "member status"), listOf("risk score", "mitigation playbook"))
                )
            ),
            model(
                FinancialGroupModel.ASCA,
                "Accumulating savings groups with periodic shared distributions.",
                mappedGroupTypes = listOf(GroupType.COMMUNITY_SAVINGS, GroupType.STOKVEL),
                backendFunctions = listOf(
                    fn("calculate_share_value", "day_close", "Update member equity shares."),
                    fn("allocate_surplus", "period_close", "Distribute returns by share ownership.")
                ),
                tables = listOf(
                    table("group_ledger", "id,groupId,amount,balanceAfter", "Double-entry compatible ledger basis."),
                    table("contributions", "id,group_id,amount,status", "Contribution source of truth for share accrual.")
                ),
                apis = listOf(
                    api("asca.getSharebook", "GET", "/groups/{id}/asca/sharebook", "Read member equity position."),
                    api("asca.closePeriod", "POST", "/groups/{id}/asca/close", "Close period and lock allocations.")
                ),
                workflows = listOf(
                    flow("Quarterly distribution", listOf("mark period final", "calculate net surplus", "approve distribution", "publish statements"), listOf("period.closed", "surplus.calculated", "distribution.published"))
                ),
                ai = listOf(
                    ai("Surplus projection", "Forecast close-period surplus variance.", listOf("contribution cadence", "expense trends"), listOf("confidence range", "allocation recommendation"))
                )
            ),
            model(
                FinancialGroupModel.INVESTMENT_GROUP,
                "Capital pooling for portfolio growth and member NAV tracking.",
                mappedGroupTypes = listOf(GroupType.INVESTMENT_CLUB, GroupType.TONTINE),
                backendFunctions = listOf(
                    fn("mark_to_market", "market_close", "Refresh portfolio valuation."),
                    fn("compute_nav", "valuation_updated", "Compute NAV per unit and return metrics.")
                ),
                tables = listOf(
                    table("groups", "id,type,goal_amount", "Targets and capital mandate constraints."),
                    table("group_health_scores", "group_id,overall_score", "Risk and quality governance indicators.")
                ),
                apis = listOf(
                    api("invest.nav", "GET", "/groups/{id}/investment/nav", "Read NAV, CAGR, Sharpe trend."),
                    api("invest.rebalance", "POST", "/groups/{id}/investment/rebalance", "Submit/approve rebalance plan.")
                ),
                workflows = listOf(
                    flow("Rebalance loop", listOf("collect holdings", "evaluate drift", "approve rebalance", "execute and reconcile"), listOf("valuation.updated", "rebalance.proposed", "rebalance.executed"))
                ),
                ai = listOf(
                    ai("Portfolio policy copilot", "Generate compliant rebalance proposals.", listOf("portfolio snapshot", "risk policy"), listOf("trade plan", "policy checks"))
                )
            ),
            model(
                FinancialGroupModel.EMERGENCY_FUND,
                "Liquidity-first reserve pools optimized for rapid disbursement.",
                mappedGroupTypes = listOf(GroupType.EMERGENCY_FUND),
                backendFunctions = listOf(
                    fn("reserve_coverage", "daily", "Measure months of coverage vs target."),
                    fn("incident_disbursement", "claim_approved", "Pay emergency claims with traceable approvals.")
                ),
                tables = listOf(
                    table("burial_claims", "id,group_id,status", "Reusable claim lifecycle pattern for emergency requests."),
                    table("payments", "id,group_id,status", "Settlement records and reconciliation hooks.")
                ),
                apis = listOf(
                    api("emergency.coverage", "GET", "/groups/{id}/emergency/coverage", "Read reserve adequacy indicators."),
                    api("emergency.request", "POST", "/groups/{id}/emergency/request", "Submit incident request.")
                ),
                workflows = listOf(
                    flow("Emergency payout", listOf("submit incident", "multi-approver review", "fund release", "post-incident review"), listOf("incident.submitted", "incident.approved", "payout.settled"))
                ),
                ai = listOf(
                    ai("Coverage early warning", "Flag reserve depletion risks.", listOf("reserve trend", "incident frequency"), listOf("alert", "top-up plan"))
                )
            ),
            model(
                FinancialGroupModel.BURIAL_SOCIETY,
                "Actuarial claim pools for funeral and dependent support.",
                mappedGroupTypes = listOf(GroupType.BURIAL_SOCIETY),
                backendFunctions = listOf(
                    fn("claim_assessment", "claim_submitted", "Evaluate policy and beneficiary eligibility."),
                    fn("solvency_check", "month_close", "Compute solvency and reserve adequacy.")
                ),
                tables = listOf(
                    table("burial_claims", "id,member_id,beneficiary_id,status", "Primary claim register."),
                    table("beneficiaries", "id,member_id,is_over_65", "Benefit exposure model.")
                ),
                apis = listOf(
                    api("burial.claim.submit", "POST", "/groups/{id}/burial/claims", "Submit and validate claim."),
                    api("burial.solvency", "GET", "/groups/{id}/burial/solvency", "Read solvency scorecard.")
                ),
                workflows = listOf(
                    flow("Claim lifecycle", listOf("intake", "document verification", "adjudication", "payout"), listOf("claim.submitted", "claim.verified", "claim.approved", "claim.paid"))
                ),
                ai = listOf(
                    ai("Claim triage agent", "Prioritize and route claims by urgency and confidence.", listOf("claim payload", "document quality"), listOf("triage class", "review queue"))
                )
            ),
            model(
                FinancialGroupModel.GROCERY_GROUP,
                "Bulk-buy cooperative groups with seasonal stock planning.",
                mappedGroupTypes = listOf(GroupType.STOKVEL, GroupType.COMMUNITY_SAVINGS),
                backendFunctions = listOf(
                    fn("bulk_order_plan", "weekly", "Build supplier purchase orders from member baskets."),
                    fn("supplier_settlement", "invoice_approved", "Settle supplier invoices and allocate costs.")
                ),
                tables = listOf(
                    table("payments", "id,group_id,payment_type,status", "Supplier settlement and contribution receipts."),
                    table("notifications", "id,group_id,trigger_event", "Order windows and collection reminders.")
                ),
                apis = listOf(
                    api("grocery.window.open", "POST", "/groups/{id}/grocery/windows", "Open order cycle."),
                    api("grocery.allocate", "POST", "/groups/{id}/grocery/allocate", "Allocate invoice to members.")
                ),
                workflows = listOf(
                    flow("Bulk-buy cycle", listOf("open basket window", "aggregate demand", "submit PO", "collect and distribute"), listOf("basket.window.opened", "po.submitted", "goods.received"))
                ),
                ai = listOf(
                    ai("Demand forecaster", "Predict item-level demand and stockout risk.", listOf("historical baskets", "seasonality"), listOf("forecast", "purchase suggestion"))
                )
            ),
            model(
                FinancialGroupModel.BUSINESS_CAPITAL_GROUP,
                "Member capital pools funding SME or side-hustle initiatives.",
                mappedGroupTypes = listOf(GroupType.INVESTMENT_CLUB, GroupType.COMMUNITY_SAVINGS),
                backendFunctions = listOf(
                    fn("deal_scoring", "proposal_submitted", "Score funding proposals against policy."),
                    fn("milestone_release", "milestone_approved", "Release staged capital tranches.")
                ),
                tables = listOf(
                    table("loans", "id,group_id,amount,status", "Capital deployment and repayment tracking."),
                    table("loan_repayments", "loan_id,amount,paid_at", "Cashback and yield realization records.")
                ),
                apis = listOf(
                    api("capital.proposal.submit", "POST", "/groups/{id}/capital/proposals", "Submit investment proposal."),
                    api("capital.tranche.release", "POST", "/groups/{id}/capital/tranches/{id}/release", "Approve and disburse tranche.")
                ),
                workflows = listOf(
                    flow("Capital lifecycle", listOf("proposal intake", "committee vote", "tranche disbursement", "repayment monitoring"), listOf("proposal.submitted", "proposal.approved", "tranche.released", "repayment.posted"))
                ),
                ai = listOf(
                    ai("Proposal risk copilot", "Estimate probability of repayment default.", listOf("proposal data", "group repayment history"), listOf("risk grade", "covenant suggestions"))
                )
            ),
            model(
                FinancialGroupModel.EDUCATION_SAVINGS_GROUP,
                "Goal-based pools for school fees and educational milestones.",
                mappedGroupTypes = listOf(GroupType.COMMUNITY_SAVINGS, GroupType.EMERGENCY_FUND),
                backendFunctions = listOf(
                    fn("tuition_projection", "month_close", "Forecast funding sufficiency by term."),
                    fn("term_disbursement", "term_start", "Issue scheduled education payouts.")
                ),
                tables = listOf(
                    table("groups", "id,goal_amount,period_months", "Savings horizon and target modeling."),
                    table("contributions", "group_id,due_date,status", "Planned contribution cadence.")
                ),
                apis = listOf(
                    api("education.forecast", "GET", "/groups/{id}/education/forecast", "Read term-by-term readiness."),
                    api("education.payout.schedule", "POST", "/groups/{id}/education/payouts/schedule", "Set term disbursement plan.")
                ),
                workflows = listOf(
                    flow("Term funding", listOf("validate target", "collect top-ups", "schedule payout", "confirm settlement"), listOf("target.recalculated", "topup.posted", "payout.scheduled"))
                ),
                ai = listOf(
                    ai("Fee inflation planner", "Recommend dynamic contribution increments.", listOf("historical inflation", "target horizon"), listOf("updated contribution path", "gap alerts"))
                )
            ),
            model(
                FinancialGroupModel.SOCIAL_CREDIT_SYSTEM,
                "Trust-based member scoring and contribution incentives.",
                mappedGroupTypes = listOf(GroupType.COMMUNITY_SAVINGS, GroupType.OTHER),
                backendFunctions = listOf(
                    fn("credit_score_update", "contribution_posted", "Refresh reliability score."),
                    fn("incentive_allocation", "period_close", "Apply rewards/limits from social credit bands.")
                ),
                tables = listOf(
                    table("members", "id,group_id,status,total_paid", "Signal source for score updates."),
                    table("notifications", "group_id,member_id,message", "Transparency notices for score changes.")
                ),
                apis = listOf(
                    api("social.score.get", "GET", "/groups/{id}/social/scores/{memberId}", "Read member trust score."),
                    api("social.policy.update", "POST", "/groups/{id}/social/policies", "Update incentive rules.")
                ),
                workflows = listOf(
                    flow("Trust cycle", listOf("collect behavioral events", "recompute score", "publish explanation", "apply limits"), listOf("behavior.event", "score.updated", "policy.enforced"))
                ),
                ai = listOf(
                    ai("Behavior anomaly detector", "Detect sudden trust deterioration.", listOf("payment velocity", "status transitions"), listOf("anomaly alert", "follow-up actions"))
                )
            ),
            model(
                FinancialGroupModel.HYBRID_FINANCIAL_GROUP,
                "Composable groups combining multiple financial models under policy controls.",
                mappedGroupTypes = listOf(GroupType.OTHER),
                backendFunctions = listOf(
                    fn("policy_router", "command_received", "Route operation to model-specific policy pipeline."),
                    fn("composite_settlement", "period_close", "Net settlements across sub-pools.")
                ),
                tables = listOf(
                    table("groups", "id,type,description", "Stores hybrid profile metadata."),
                    table("group_ledger", "groupId,category,amount", "Sub-ledger categories for hybrid pools.")
                ),
                apis = listOf(
                    api("hybrid.policy.compose", "POST", "/groups/{id}/hybrid/policies", "Attach model mix and guardrails."),
                    api("hybrid.snapshot", "GET", "/groups/{id}/hybrid/snapshot", "Read cross-model KPIs." )
                ),
                workflows = listOf(
                    flow("Hybrid close", listOf("evaluate sub-model health", "run policy router", "net positions", "publish governance report"), listOf("submodel.health.updated", "policy.route.executed", "hybrid.close.completed"))
                ),
                ai = listOf(
                    ai("Hybrid optimizer", "Recommend model weights based on risk and goals.", listOf("sub-model KPIs", "governance constraints"), listOf("weight proposal", "impact simulation"))
                )
            )
        ),
        eventArchitecture = EventArchitecture(
            eventBus = "Outbox + Supabase Realtime + Worker consumers",
            coreEvents = listOf(
                "contribution.posted",
                "payout.requested",
                "payout.settled",
                "claim.submitted",
                "claim.approved",
                "ledger.entry.posted",
                "risk.alert.raised"
            ),
            deliveryGuarantee = "At-least-once with idempotent handlers",
            idempotencyStrategy = "Natural keys + operation_id dedupe table"
        ),
        treasuryArchitecture = TreasuryArchitecture(
            ledgerModel = "Double-entry compatible event-sourced ledger",
            subLedgers = listOf("member_contributions", "claims", "payouts", "fees", "reserves"),
            reconciliationFrequency = "Daily incremental, monthly hard close",
            liquidityRules = listOf(
                "Maintain minimum reserve threshold per group policy",
                "Block payout when solvency/coverage rule is breached",
                "Enforce two-step approval above configured threshold"
            )
        ),
        governanceSystem = GovernanceSystem(
            decisionModels = listOf("admin_approval", "committee_vote", "weighted_vote", "dual_control"),
            controls = listOf(
                "Role-based authorization",
                "Policy-as-code validations",
                "Immutable audit log for critical actions"
            ),
            auditArtifacts = listOf("decision records", "vote trails", "policy snapshots", "settlement attestations")
        ),
        riskFramework = RiskFramework(
            categories = listOf("liquidity", "credit", "fraud", "operational", "compliance"),
            controls = listOf(
                "transaction velocity checks",
                "member status gates",
                "step-up approval for unusual disbursements",
                "periodic stress testing"
            ),
            monitoringSignals = listOf(
                "default_risk_score",
                "cycle_completion_probability",
                "reserve_coverage_months",
                "payment_failure_rate"
            )
        ),
        paymentInfrastructure = PaymentInfrastructure(
            rails = listOf("YoCo", "EFT", "cash-capture with dual confirmation"),
            settlementModes = listOf("instant", "T+1", "scheduled batch"),
            fraudControls = listOf(
                "beneficiary account verification",
                "transaction limit tiers",
                "webhook signature verification",
                "replay prevention via idempotency keys"
            )
        ),
        recommendedMicroservices = listOf(
            MicroserviceBlueprint("Group Policy Service", "Own group policy definitions and validation.", listOf("groups", "policy_versions")),
            MicroserviceBlueprint("Treasury Ledger Service", "Own posting rules and ledger integrity.", listOf("group_ledger", "ledger_locks")),
            MicroserviceBlueprint("Payout Service", "Own payout orchestration and settlement callbacks.", listOf("payouts", "settlement_events")),
            MicroserviceBlueprint("Risk & Scoring Service", "Own risk signals and anomaly models.", listOf("risk_scores", "risk_alerts")),
            MicroserviceBlueprint("Notification Service", "Own outbound channels and templates.", listOf("notifications", "delivery_receipts"))
        ),
        futureEvolution = listOf(
            EvolutionMilestone(
                phase = "Phase 1 - Modular Monolith",
                objective = "Consolidate policy and workflow abstractions without splitting deployment units.",
                deliverables = listOf("Policy router", "Outbox dispatcher", "Blueprint-driven API scaffolding")
            ),
            EvolutionMilestone(
                phase = "Phase 2 - Service Extraction",
                objective = "Extract payout and ledger services behind stable contracts.",
                deliverables = listOf("Payout service", "Ledger service", "Event schema registry")
            ),
            EvolutionMilestone(
                phase = "Phase 3 - Intelligent Operations",
                objective = "Deploy domain-specific AI agents for risk, treasury, and governance copilots.",
                deliverables = listOf("Risk copilot", "Treasury recon agent", "Governance exception agent")
            )
        )
    )

    fun forModel(model: FinancialGroupModel): GroupModelBlueprint? =
        current().groupModels.firstOrNull { it.model == model }

    private fun model(
        model: FinancialGroupModel,
        summary: String,
        mappedGroupTypes: List<GroupType>,
        backendFunctions: List<BackendFunction>,
        tables: List<DatabaseTable>,
        apis: List<ApiOperation>,
        workflows: List<WorkflowBlueprint>,
        ai: List<AiAgentOpportunity>
    ): GroupModelBlueprint = GroupModelBlueprint(
        model = model,
        summary = summary,
        mappedGroupTypes = mappedGroupTypes,
        backendFunctions = backendFunctions,
        databaseTables = tables,
        apiSurface = apis,
        workflows = workflows,
        aiAgentOpportunities = ai
    )

    private fun fn(name: String, trigger: String, purpose: String) =
        BackendFunction(name = name, trigger = trigger, purpose = purpose)

    private fun table(name: String, keyColumns: String, notes: String) =
        DatabaseTable(name = name, keyColumns = keyColumns.split(',').map { it.trim() }, notes = notes)

    private fun api(operationId: String, method: String, path: String, purpose: String) =
        ApiOperation(operationId = operationId, method = method, path = path, purpose = purpose)

    private fun flow(name: String, steps: List<String>, events: List<String>) =
        WorkflowBlueprint(name = name, steps = steps, events = events)

    private fun ai(name: String, goal: String, inputs: List<String>, outputs: List<String>) =
        AiAgentOpportunity(name = name, goal = goal, inputs = inputs, outputs = outputs)
}

