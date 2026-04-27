# ANDROID AI AGENT CONTEXT — SANIBONASAVE

## Overview

SanibonaSave is a **community-based financial coordination app** designed for:

* Low-income users
* Irregular income earners
* Group-based financial responsibility (stokvel model)

The system enables:

* Multi-contributor policies (e.g. funeral cover)
* Flexible contributions (no fixed monthly requirement)
* Real-time policy funding status
* Risk detection for contribution drop-off

---

## Core Architecture

### Backend
- Supabase (PostgreSQL + Edge Functions + Realtime)
- Server-side RPCs for atomic financial transactions (e.g., `record_contribution_v1`).

### Android App
- Kotlin (Version 2.1.0)
- MVVM + Clean Architecture with Repository Pattern.
- Room (Offline-first local cache with `observeAndSync` pattern).
- Supabase Kotlin SDK (PostgREST, Auth, Realtime, Storage).
- Compose (Declarative UI).
- Hilt (Dependency Injection).
- Coroutines + Flow (Reactive data streams).

---

## Key Concepts

### Policy

A shared financial agreement.

Fields:

* id
* name
* required_amount
* status (ACTIVE, PARTIAL, INACTIVE)

---

### Contributor

A user who contributes money toward a policy.

* Multiple contributors per policy
* Contributions are flexible (any amount)

---

### Contribution

A payment event:

* policy_id
* user_id
* amount
* timestamp

---

### Beneficiary

Person covered by the policy.

---

## Backend Endpoints

### 1. Contribute

POST `/functions/v1/contribute`

Request:

```json
{
  "policy_id": "uuid",
  "user_id": "uuid",
  "amount": 50
}
```

Response:

```json
{
  "success": true
}
```

---

### 2. Policy Status

POST `/functions/v1/policy-status`

Response:

```json
{
  "total": 300,
  "required": 500,
  "ratio": 0.6,
  "status": "PARTIAL"
}
```

---

### 3. Create Policy

POST `/functions/v1/create-policy`

---

### 4. Risk Detection

POST `/functions/v1/risk-detection`

Returns users at risk of stopping contributions.

---

## Android Module Structure

### 1. policy-engine

Handles:

* Policy display
* Contribution progress
* Status updates

Key classes:

* PolicyRepository
* PolicyViewModel
* PolicyApiService

---

### 2. contribution-engine

Handles:

* Sending contributions
* Tracking history

---

### 3. group-engine

Handles:

* Stokvel/group logic
* Shared responsibility

---

### 4. notification-engine

Handles:

* Alerts
* Push notifications
* WhatsApp integration (future)

---

## Data Models (Kotlin)

```kotlin
data class Policy(
    val id: String,
    val name: String,
    val required_amount: Double,
    val status: String
)

data class ContributionRequest(
    val policy_id: String,
    val user_id: String,
    val amount: Double
)

data class PolicyStatusResponse(
    val total: Double,
    val required: Double,
    val ratio: Double,
    val status: String
)
```

---

## Business Logic Rules

### Policy Activation

```
IF total >= required → ACTIVE
IF total > 0 → PARTIAL
IF total == 0 → INACTIVE
```

---

### Member & Contribution Logic
- **Flexible but Tracked**: While the system supports flexible contributions, it calculates `totalDueNow` based on join dates and monthly requirements.
- **Financial Metrics**:
    - `totalPaid`: Cumulative lifetime contributions.
    - `shortfall`: Amount missing to be "Up to Date" for the current month.
    - `overpayment`: Credit balance for future months.
- **Partial Payments**: Controlled by group-level `allow_partial_payment` setting.
- **Status Determination**: Members are `ACTIVE`, `PROBATION`, or `SUSPENDED` based on payment history and group grace periods.

---

### Social Model

* Shared responsibility
* Group accountability
* No penalties for missed payments
* System adapts instead of punishing users

---

## UI Requirements

### Policy Screen

Must display:

* Total contributed
* Required amount
* Progress bar
* Status (ACTIVE / PARTIAL)

---

### Contribution Flow

User should:

1. Enter amount
2. Confirm
3. See updated progress instantly

---

### Notifications

Trigger when:

* Policy < 70% funded
* Contributor inactive

---

## AI AGENT INSTRUCTIONS

When generating code:

1. ALWAYS use MVVM pattern
2. Use Supabase Kotlin SDK (PostgREST/Auth/Realtime/Storage) for backend calls in this project
3. ALWAYS use coroutines (suspend functions)
4. Keep UI simple (low-income users)
5. Avoid heavy dependencies
6. Prefer offline-first design where possible
7. For contributions, use atomic RPC flow (`record_contribution_v1`) and keep `p_type` mapped to `contributions.type`
8. Do not silently coerce unknown payment types; return a friendly validation error

---

## Constraints

* Must support low-end Android devices
* Must minimize data usage
* Must work with intermittent connectivity

---

## Future Extensions

* Payment integration (Ozow / EFT)
* WhatsApp bot interface
* AI prediction engine
* Admin dashboard

---

## Summary

SanibonaSave is not just a fintech app.

It is a:
→ Distributed financial safety network
→ Built on community contribution
→ Designed for economic resilience

The Android app must reflect:

* simplicity
* trust
* flexibility
* shared responsibility
