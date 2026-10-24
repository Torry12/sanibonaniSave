# SanibonaniSave Architecture Guidance Context

## Purpose

This document provides architectural, security, compliance, scalability, and engineering guidance for coding agents and developers working on the SanibonaniSave platform.

SanibonaniSave should NOT be designed as a simple savings application.

It should be treated as:

```text
Distributed Cooperative Financial Infrastructure
```

The platform must eventually support:
- stokvels
- ROSCA groups
- digital cooperative finance
- pooled savings
- treasury management
- investments
- payouts
- reconciliation
- identity verification
- fraud prevention
- embedded finance
- AI-driven orchestration

---

# Core Engineering Principles

## 1. Immutable Financial Ledger

NEVER directly mutate balances.

BAD:

```python
user.balance += amount
```

CORRECT:

```text
Balance = Sum(valid ledger entries)
```

All financial state must derive from immutable ledger entries.

Required entities:

```text
LedgerEntry
Transaction
SettlementRecord
ReconciliationRecord
AuditLog
```

Every financial action must:
- be traceable
- be reversible via compensating transaction
- support auditing
- support reconciliation

---

# 2. Event-Driven Architecture

DO NOT build tightly coupled synchronous workflows.

BAD:

```text
API -> DB -> Payment -> Notification
```

Use asynchronous event-driven architecture.

Recommended:

```text
payment.completed
payment.failed
kyc.verified
member.joined
contribution.posted
payout.executed
risk.flagged
```

Recommended technologies:
- RabbitMQ
- Kafka
- Redis Streams
- NATS

All critical financial operations should emit events.

---

# 3. Modular Microservice-Oriented Design

Avoid monolithic business logic.

Recommended services:

```text
Identity Service
Ledger Service
Payments Service
Treasury Service
Group Service
Risk Service
Notification Service
Compliance Service
Analytics Service
Investment Service
AI Orchestration Service
```

Each service should:
- own its data domain
- expose APIs/events
- remain independently deployable

---

# 4. Payment Abstraction Layer

DO NOT tightly couple payment providers into business logic.

BAD:

```python
if provider == "payfast":
```

CORRECT:

```python
class PaymentProvider:
    def collect(self):
        pass

    def disburse(self):
        pass

    def refund(self):
        pass

    def verify(self):
        pass
```

Implement adapters:

```text
PayFastAdapter
OzowAdapter
PeachPaymentsAdapter
YocoAdapter
BankAPIAdapter
```

All providers must conform to a shared interface.

---

# 5. Reconciliation Engine

The system MUST support reconciliation.

Reconciliation compares:

```text
Internal Ledger
vs
Provider Records
vs
Bank Settlement Records
```

Required capabilities:
- settlement tracking
- webhook verification
- retry queues
- discrepancy detection
- automated repair jobs
- audit history

Never assume provider success equals ledger success.

---

# 6. Fraud and Risk Engine

Stokvel and group finance systems are high-risk environments.

Threats include:
- fake identities
- synthetic accounts
- mule accounts
- payout abuse
- collusion fraud
- account takeover
- social engineering

Required fraud capabilities:

```text
Device Fingerprinting
Behavioral Analysis
Velocity Checks
Duplicate Identity Detection
Anomaly Detection
Geo-Risk Analysis
Transaction Monitoring
SIM Swap Detection
```

Recommended architecture:

```text
Risk Score =
identity_risk +
device_risk +
transaction_risk +
behavior_risk +
group_risk
```

---

# 7. Compliance Architecture

The platform must support South African regulatory requirements.

Required compliance domains:
- FICA
- AML
- POPIA
- KYC
- sanctions screening
- PEP screening

Required services:

```text
KYC Engine
Consent Management
Audit Logging
AML Monitoring
PEP Screening
Document Verification
Identity Verification
```

All sensitive data must support:
- encryption at rest
- encryption in transit
- access auditing
- retention policies
- consent tracking

---

# 8. Identity Verification Layer

Identity systems should be provider-agnostic.

Recommended providers:
- WhoYou
- VerifyNow
- Gathr
- YeboVerify

Architecture:

```text
Identity Orchestrator
    -> DHA Verification
    -> Face Match
    -> Liveness Detection
    -> AML Screening
```

Never hardcode provider assumptions.

---

# 9. Treasury Architecture

Group funds must support treasury management.

Required treasury capabilities:
- pooled funds
- reserve management
- liquidity management
- payout scheduling
- investment allocation
- treasury risk scoring

Treasury operations must always:
- generate ledger entries
- support approvals
- support multi-signature workflows
- support audit logging

---

# 10. Governance Layer

Groups are social-financial systems.

Required governance capabilities:

```text
Voting
Approvals
Quorum Rules
Dispute Resolution
Treasurer Permissions
Multi-Signature Payouts
Role-Based Access Control
```

---

# 11. Database Architecture

Recommended stack:

## PostgreSQL
Use for:
- ledger
- transactions
- balances
- reconciliation
- audit logs
- treasury

## Redis
Use for:
- queues
- caching
- rate limiting
- temporary state

## Optional MongoDB
Use for:
- analytics
- AI memory
- activity feeds
- unstructured logs

Do NOT rely entirely on MongoDB for financial state.

---

# 12. Security Requirements

Critical requirements:

```text
Encrypted PII
JWT Rotation
RBAC
Webhook Signature Verification
Rate Limiting
Secrets Management
KMS/HSM Integration
Audit Trails
Tokenized Bank Data
```

Never:
- commit secrets
- store plaintext sensitive data
- trust external webhook payloads without validation
- expose internal IDs unnecessarily

Recommended:
- Hashicorp Vault
- AWS KMS
- Azure Key Vault
- OpenTelemetry tracing

---

# 13. AI Orchestration Architecture

Future AI architecture should support specialized agents.

Recommended AI agents:

```text
Supervisor Agent
Fraud Agent
Treasury Agent
Collections Agent
Risk Agent
Payment Routing Agent
Engagement Agent
Compliance Agent
```

Recommended technologies:
- LangGraph
- OpenAI Agents SDK
- MCP architecture
- vector memory systems

Agents should communicate through:
- events
- queues
- orchestration workflows

---

# 14. Scalability Principles

System must support:
- high transaction concurrency
- asynchronous workflows
- retry-safe operations
- idempotent financial processing

Required patterns:

```text
Idempotency Keys
Retry Queues
Saga Patterns
Circuit Breakers
Dead Letter Queues
```

Recommended infrastructure:
- Kubernetes
- Docker
- Celery/Dramatiq
- Prometheus
- Grafana
- ELK Stack
- Sentry

---

# 15. API Design Standards

Requirements:
- versioned APIs
- idempotent transaction endpoints
- OpenAPI documentation
- strict validation
- pagination
- rate limiting

Recommended standards:

```text
/api/v1/
```

Use:
- FastAPI
- Pydantic
- OpenAPI schemas

---

# 16. Group Financial Modeling

Groups should support:

```text
Contribution Cycles
Rotational Payouts
Penalty Systems
Emergency Withdrawals
Investment Pools
Savings Goals
Voting Rules
Treasurer Controls
```

Suggested domain model:

```text
Group
 ├── Treasury
 ├── Members
 ├── Rules
 ├── Cycles
 ├── Contributions
 ├── PayoutQueue
 ├── Governance
 ├── RiskProfile
```

---

# 17. Observability and Monitoring

Required observability:

```text
Distributed Tracing
Centralized Logs
Fraud Dashboards
Payment Monitoring
Anomaly Detection
Financial Health Metrics
```

Recommended stack:
- OpenTelemetry
- Prometheus
- Grafana
- Loki
- ELK
- Sentry

---

# 18. Engineering Priorities

## Phase 1

Build:
- immutable ledger
- payment abstraction
- KYC integration
- audit logging
- RBAC
- transaction workflows

## Phase 2

Build:
- event-driven architecture
- reconciliation engine
- fraud engine
- treasury management
- governance systems

## Phase 3

Build:
- AI orchestration
- predictive treasury systems
- autonomous risk monitoring
- embedded finance infrastructure
- investment optimization

---

# 19. Coding Standards

All code should:
- remain modular
- support testing
- avoid business logic duplication
- separate domain logic from infrastructure
- avoid tight coupling

Required practices:
- unit tests
- integration tests
- typed schemas
- repository pattern
- service layer separation
- async-safe transaction handling

---

# 20. Critical Non-Negotiables

NEVER:
- mutate balances directly
- trust external payment state blindly
- tightly couple providers
- store secrets in repositories
- bypass audit logging
- skip reconciliation
- allow untracked financial mutations

ALWAYS:
- use immutable ledgers
- emit events
- log financial operations
- verify payment states
- enforce RBAC
- validate identities
- design for auditability

---

# Long-Term Vision

SanibonaniSave should evolve into:

```text
Programmable Cooperative Financial Infrastructure
```

Potential future capabilities:
- embedded banking
- AI treasury management
- cooperative investing
- decentralized governance
- intelligent savings optimization
- cross-border remittances
- programmable group finance
- financial reputation systems
- credit/risk modeling

The architecture should therefore prioritize:
- modularity
- auditability
- security
- interoperability
- scalability
- provider abstraction
- intelligent orchestration

