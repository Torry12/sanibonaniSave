export type FinancialModelId =
  | "rosca"
  | "asca"
  | "investment_group"
  | "emergency_fund"
  | "burial_society"
  | "grocery_group"
  | "business_capital_group"
  | "education_savings_group"
  | "social_credit_system"
  | "hybrid_financial_group";

export type ApiOperation = {
  operation_id: string;
  method: "GET" | "POST";
  path: string;
  purpose: string;
};

export type AiOpportunity = {
  name: string;
  goal: string;
  inputs: string[];
  outputs: string[];
};

export type ModelBlueprint = {
  id: FinancialModelId;
  summary: string;
  backend_functions: string[];
  database_tables: string[];
  api_operations: ApiOperation[];
  workflows: string[];
  ai_agent_opportunities: AiOpportunity[];
};

export type CoreEventSchema = {
  event_type: string;
  aggregate_type: string;
  payload_fields: string[];
};

export const ARCHITECTURE_BLUEPRINT = {
  version: "2026.05.13",
  models: [
    {
      id: "rosca",
      summary: "Rotating payout groups with explicit rotation governance.",
      backend_functions: ["calculate_rotation", "apply_payout"],
      database_tables: ["groups", "payouts", "group_ledger"],
      api_operations: [
        { operation_id: "rosca.getSchedule", method: "GET", path: "/groups/{id}/rosca/schedule", purpose: "Read cycle schedule." },
        { operation_id: "rosca.rotate", method: "POST", path: "/groups/{id}/rosca/rotate", purpose: "Advance cycle and pick recipient." }
      ],
      workflows: ["monthly_rotation"],
      ai_agent_opportunities: [
        {
          name: "Defaulter pressure predictor",
          goal: "Predict cycle drop-out pressure.",
          inputs: ["member_status", "contribution_history"],
          outputs: ["risk_score", "mitigation_steps"]
        }
      ]
    },
    {
      id: "asca",
      summary: "Accumulating savings groups with periodic surplus sharing.",
      backend_functions: ["calculate_share_value", "allocate_surplus"],
      database_tables: ["group_ledger", "contributions"],
      api_operations: [
        { operation_id: "asca.getSharebook", method: "GET", path: "/groups/{id}/asca/sharebook", purpose: "Read member share positions." },
        { operation_id: "asca.closePeriod", method: "POST", path: "/groups/{id}/asca/close", purpose: "Close period and distribute surplus." }
      ],
      workflows: ["quarterly_distribution"],
      ai_agent_opportunities: [
        {
          name: "Surplus projection",
          goal: "Forecast period-end surplus and distribution variance.",
          inputs: ["contribution_trends", "expense_trends"],
          outputs: ["surplus_forecast", "distribution_recommendation"]
        }
      ]
    },
    {
      id: "investment_group",
      summary: "Investment pools with NAV and performance controls.",
      backend_functions: ["mark_to_market", "compute_nav"],
      database_tables: ["groups", "group_health_scores"],
      api_operations: [
        { operation_id: "invest.nav", method: "GET", path: "/groups/{id}/investment/nav", purpose: "Read valuation metrics." },
        { operation_id: "invest.rebalance", method: "POST", path: "/groups/{id}/investment/rebalance", purpose: "Submit rebalance proposal." }
      ],
      workflows: ["rebalance_loop"],
      ai_agent_opportunities: [
        {
          name: "Portfolio policy copilot",
          goal: "Generate policy-compliant rebalance plans.",
          inputs: ["portfolio_snapshot", "risk_policy"],
          outputs: ["rebalance_plan", "policy_exceptions"]
        }
      ]
    },
    {
      id: "emergency_fund",
      summary: "Liquidity-first reserve pools for rapid emergency support.",
      backend_functions: ["reserve_coverage", "incident_disbursement"],
      database_tables: ["payments", "burial_claims"],
      api_operations: [
        { operation_id: "emergency.coverage", method: "GET", path: "/groups/{id}/emergency/coverage", purpose: "Read reserve adequacy." },
        { operation_id: "emergency.request", method: "POST", path: "/groups/{id}/emergency/request", purpose: "Submit emergency request." }
      ],
      workflows: ["emergency_payout"],
      ai_agent_opportunities: [
        {
          name: "Coverage early warning",
          goal: "Detect likely reserve depletion.",
          inputs: ["reserve_trend", "incident_frequency"],
          outputs: ["alert", "top_up_plan"]
        }
      ]
    },
    {
      id: "burial_society",
      summary: "Actuarial claim pools for funeral risk-sharing.",
      backend_functions: ["claim_assessment", "solvency_check"],
      database_tables: ["burial_claims", "beneficiaries"],
      api_operations: [
        { operation_id: "burial.claim.submit", method: "POST", path: "/groups/{id}/burial/claims", purpose: "Submit burial claim." },
        { operation_id: "burial.solvency", method: "GET", path: "/groups/{id}/burial/solvency", purpose: "Read solvency metrics." }
      ],
      workflows: ["claim_lifecycle"],
      ai_agent_opportunities: [
        {
          name: "Claim triage agent",
          goal: "Prioritize claim review queues.",
          inputs: ["claim_payload", "documents"],
          outputs: ["priority", "routing_decision"]
        }
      ]
    },
    {
      id: "grocery_group",
      summary: "Bulk-buy grocery groups with supplier planning cycles.",
      backend_functions: ["bulk_order_plan", "supplier_settlement"],
      database_tables: ["payments", "notifications"],
      api_operations: [
        { operation_id: "grocery.window.open", method: "POST", path: "/groups/{id}/grocery/windows", purpose: "Open ordering window." },
        { operation_id: "grocery.allocate", method: "POST", path: "/groups/{id}/grocery/allocate", purpose: "Allocate supplier invoice." }
      ],
      workflows: ["bulk_buy_cycle"],
      ai_agent_opportunities: [
        {
          name: "Demand forecaster",
          goal: "Predict order quantities and stockout risk.",
          inputs: ["historical_baskets", "seasonality"],
          outputs: ["item_forecast", "purchase_plan"]
        }
      ]
    },
    {
      id: "business_capital_group",
      summary: "Capital pools financing member business proposals.",
      backend_functions: ["deal_scoring", "milestone_release"],
      database_tables: ["loans", "loan_repayments"],
      api_operations: [
        { operation_id: "capital.proposal.submit", method: "POST", path: "/groups/{id}/capital/proposals", purpose: "Submit funding proposal." },
        { operation_id: "capital.tranche.release", method: "POST", path: "/groups/{id}/capital/tranches/{id}/release", purpose: "Release approved tranche." }
      ],
      workflows: ["capital_lifecycle"],
      ai_agent_opportunities: [
        {
          name: "Proposal risk copilot",
          goal: "Estimate proposal default risk.",
          inputs: ["proposal_data", "repayment_history"],
          outputs: ["risk_grade", "covenant_suggestions"]
        }
      ]
    },
    {
      id: "education_savings_group",
      summary: "Goal-based pools for tuition and education milestones.",
      backend_functions: ["tuition_projection", "term_disbursement"],
      database_tables: ["groups", "contributions"],
      api_operations: [
        { operation_id: "education.forecast", method: "GET", path: "/groups/{id}/education/forecast", purpose: "Read education funding forecast." },
        { operation_id: "education.payout.schedule", method: "POST", path: "/groups/{id}/education/payouts/schedule", purpose: "Plan term disbursements." }
      ],
      workflows: ["term_funding"],
      ai_agent_opportunities: [
        {
          name: "Fee inflation planner",
          goal: "Recommend contribution increments for tuition inflation.",
          inputs: ["inflation_data", "horizon"],
          outputs: ["suggested_contributions", "gap_alerts"]
        }
      ]
    },
    {
      id: "social_credit_system",
      summary: "Trust scoring and incentive systems for member behavior.",
      backend_functions: ["credit_score_update", "incentive_allocation"],
      database_tables: ["members", "notifications"],
      api_operations: [
        { operation_id: "social.score.get", method: "GET", path: "/groups/{id}/social/scores/{memberId}", purpose: "Read trust score." },
        { operation_id: "social.policy.update", method: "POST", path: "/groups/{id}/social/policies", purpose: "Update incentive policy." }
      ],
      workflows: ["trust_cycle"],
      ai_agent_opportunities: [
        {
          name: "Behavior anomaly detector",
          goal: "Detect trust deterioration anomalies.",
          inputs: ["member_events", "payment_velocity"],
          outputs: ["anomaly_alert", "follow_up_actions"]
        }
      ]
    },
    {
      id: "hybrid_financial_group",
      summary: "Composable model mixing with policy routing and net settlement.",
      backend_functions: ["policy_router", "composite_settlement"],
      database_tables: ["groups", "group_ledger"],
      api_operations: [
        { operation_id: "hybrid.policy.compose", method: "POST", path: "/groups/{id}/hybrid/policies", purpose: "Define model mix policy." },
        { operation_id: "hybrid.snapshot", method: "GET", path: "/groups/{id}/hybrid/snapshot", purpose: "Read cross-model KPIs." }
      ],
      workflows: ["hybrid_close"],
      ai_agent_opportunities: [
        {
          name: "Hybrid optimizer",
          goal: "Recommend optimal model weights by risk/goal profile.",
          inputs: ["submodel_kpis", "constraints"],
          outputs: ["weight_proposal", "impact_estimate"]
        }
      ]
    }
  ] as ModelBlueprint[],
  event_architecture: {
    bus: "outbox + realtime",
    delivery: "at_least_once",
    idempotency: "event_id_dedup"
  },
  core_events_registry: {
    version: "2026.05.13",
    events: [
      {
        event_type: "contribution.posted",
        aggregate_type: "contribution",
        payload_fields: ["contribution_id", "group_id", "member_id", "amount", "status"]
      },
      {
        event_type: "payout.requested",
        aggregate_type: "payout",
        payload_fields: ["payout_id", "group_id", "amount", "requested_by", "status"]
      },
      {
        event_type: "payout.settled",
        aggregate_type: "payout",
        payload_fields: ["payout_id", "group_id", "amount", "status", "settled_at", "transaction_id"]
      },
      {
        event_type: "claim.submitted",
        aggregate_type: "claim",
        payload_fields: ["claim_id", "group_id", "member_id", "claim_amount", "status"]
      },
      {
        event_type: "claim.approved",
        aggregate_type: "claim",
        payload_fields: ["claim_id", "group_id", "reviewed_by", "approved_amount", "status"]
      },
      {
        event_type: "ledger.entry.posted",
        aggregate_type: "ledger_entry",
        payload_fields: ["entry_id", "group_id", "category", "amount", "balance_after", "created_at"]
      },
      {
        event_type: "risk.alert.raised",
        aggregate_type: "risk_alert",
        payload_fields: ["alert_id", "group_id", "risk_type", "severity", "signal", "raised_at"]
      }
    ] as CoreEventSchema[],
    consumer_guidance: {
      delivery: "at_least_once",
      idempotency: "deduplicate by event_id and ignore already-applied events",
      ordering: "best effort by aggregate_id, not globally ordered"
    }
  }
};

export function parseModelId(input: string | null): FinancialModelId | null {
  if (!input) return null;
  const normalized = input.trim().toLowerCase() as FinancialModelId;
  const allowed = new Set<FinancialModelId>([
    "rosca",
    "asca",
    "investment_group",
    "emergency_fund",
    "burial_society",
    "grocery_group",
    "business_capital_group",
    "education_savings_group",
    "social_credit_system",
    "hybrid_financial_group"
  ]);
  return allowed.has(normalized) ? normalized : null;
}

