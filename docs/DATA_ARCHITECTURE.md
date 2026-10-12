# 📊 Data Architecture & Synchronization Guide

This document provides a deep dive into the data layer of the SanibonaniSave application, covering domain models, database schema, relationships, and the offline-first synchronization strategy.

---

## 🏗 1. Core Data Structures (Domain Models)

The domain layer (`:domain`) defines the fundamental business entities. Models are
annotated `@Serializable` for network/disk transport. Most also use `@Parcelize`
so they can travel through Compose navigation arguments — this is the reason
`:domain` still depends on the Android framework, and it is the main outstanding
item in moving `:domain` to a pure-JVM module.

> Models do **not** reference the Supabase SDK. Session state crosses the module
> boundary as the domain-owned `AuthSession` / `AuthSessionStatus`, which `:data`
> maps from the SDK types.

### Primary Entities:
- **`Group`**: The central container for all cooperative activities. Contains configuration (fees, probation, type) and financial state (balance).
- **`Member`**: Represents a person's relationship with a group. Tracks status (`ACTIVE`, `PROBATION`, `SUSPENDED`), joined date, and total contributions.
- **`Contribution`**: A specific financial obligation or payment (e.g., monthly fee, joining fee).
- **`LedgerEntry`**: The source of truth for all financial movements. Immutable records that adjust the group's balance.
- **`Loan` & `LoanRepayment`**: Lifecycle management for the "Smart Loan" feature.
- **`Beneficiary` & `BeneficiaryClaim`**: Identity and claim tracking for Burial Societies.

### Financial Models:
- **`ActuarialMetrics`**: Calculated scores and risk indicators for group health.
- **`PaymentCalculation`**: Transitional model used to compute fees, late penalties, and premiums before recording.

---

## 🗄 2. Database Schema (Room)

The local data layer (`:data`) mirrors the Supabase backend for offline capabilities.

### Table Mapping:
| Table Name | Entity Class | Primary Key | Main Indices |
|------------|--------------|-------------|--------------|
| `user_profiles` | `UserProfileEntity` | `id` (UUID) | `role`, `email` |
| `groups` | `GroupEntity` | `id` (UUID) | `admin_user_id`, `fee_status`, `is_public` |
| `members` | `MemberEntity` | `id` (UUID) | `group_id`, `user_id`, `member_key`, `status` |
| `contributions` | `ContributionEntity` | `id` (UUID) | `member_id`, `group_id`, `status`, `due_date` |
| `payouts` | `PayoutEntity` | `id` (UUID) | `group_id`, `status`, `member_id` |
| `beneficiaries` | `BeneficiaryEntity` | `id, group_id, member_id` | `group_id`, `member_id` |
| `beneficiary_payout_claims` | `BeneficiaryClaimEntity` | `id` (UUID) | `group_id`, `member_id`, `status` |
| `group_ledger` | `LedgerEntryEntity` | `id` (UUID) | `group_id`, `transaction_id`, `member_id` |
| `loans` | `LoanEntity` | `id` (UUID) | `member_id`, `group_id`, `status` |
| `loan_repayments` | `LoanRepaymentEntity` | `id` (UUID) | `loan_id`, `member_id` |
| `group_polls` | `GroupPollEntity` | `id` (UUID) | `group_id`, `status` |
| `bank_transactions` | `BankTransactionEntity` | `id` (UUID) | `group_id`, `bank_reference`, `status` |
| `member_behavior_track` | `MemberBehaviorTrackEntity` | `id` (UUID) | `member_id`, `group_id`, `fraud_risk_level` |
| `audit_logs` | `AuditLogEntity` | `id` (UUID) | `actor_id`, `target_group_id`, `action` |
| `pending_operations` | `PendingOperationEntity` | `id` (UUID) | `type`, `groupId`, `createdAt` |

### Complex Types (TypeConverters):
- **Enums**: Roles, Statuses, and Prefs are converted to/from Strings (using snake_case to match Supabase).
- **Booleans**: Stored as Integers (0/1) for SQLite compatibility.
- **Dates**: Stored as ISO-8601 Strings for consistent parsing across JVM and Postgres.

---

## 🔗 3. Entity Relationships

The system follows a relational hierarchy anchored by the `Group`.

```mermaid
erDiagram
    GROUP ||--o{ MEMBER : "contains"
    GROUP ||--o{ CONTRIBUTION : "has_obligations"
    GROUP ||--o{ PAYOUT : "disburses"
    GROUP ||--o{ LEDGER_ENTRY : "tracks_financials"
    MEMBER ||--o{ CONTRIBUTION : "makes"
    MEMBER ||--o{ BENEFICIARY : "assigns"
    MEMBER ||--o{ LOAN : "requests"
    LOAN ||--o{ LOAN_REPAYMENT : "repaid_by"
    BENEFICIARY ||--o{ BURIAL_CLAIM : "claims"
```

### Key Relational Constraints:
- **Group → Member**: Many-to-many relationship via the `members` table (a user can be a member of multiple groups).
- **User → Profile**: One-to-one mapping via the Supabase Auth `uid`.
- **Ledger → Transaction**: Optional mapping. Not every ledger entry has a `transaction_id` (e.g., manual admin adjustments), but every payment transaction MUST have a `ledger_id`.

---

## 🔄 4. Synchronization (`observeAndSync` & Outbox)

The application implements a "Local-First" pattern with an **Offline Outbox** for network resilience.

### Read Path (`observeAndSync`):
1.  **Emit Local**: Immediately returns a `Flow` from Room.
2.  **Pillar-Parallel Sync**: Simultaneously fetches from Supabase in domain-parallel blocks (Treasury, Equity, etc.).
3.  **Delta Update**: Merges remote changes into local Room cache.

### Write Path (Outbox Pattern):
1.  **Optimistic Attempt**: App attempts the Supabase RPC immediately.
2.  **Auto-Queuing**: If a network break (timeout, connect error) occurs, the full JSON payload is saved to `pending_operations`.
3.  **Background Processing**: `SyncRepository` monitors connectivity and processes the outbox in strict chronological order upon return.

### Example (BaseRepository.kt):
```kotlin
fun <T, E> observeAndSync(
    dbFlow: Flow<E>,
    mapper: (E) -> T,
    networkFetch: suspend () -> T,
    cacheSync: suspend (T) -> Unit
): Flow<Result<T>> = channelFlow {
    // 1. Observe DB
    launch {
        dbFlow.collect { entity ->
            send(Result.success(mapper(entity)))
        }
    }
    // 2. Sync from Network
    try {
        val remoteData = networkFetch()
        cacheSync(remoteData)
    } catch (e: Exception) {
        send(Result.failure(e))
    }
}
```

---

## 🛡 5. Data Integrity & Validation

### Backend Enforcement (Supabase RPCs):
Critical financial operations are performed via **Postgres Functions (RPCs)** to ensure atomicity.
- **`record_contribution_v2`**: Atomically creates a contribution, updates group balance, and creates a ledger entry. If any step fails, the whole transaction rolls back.

### Frontend Validation (`ValidationUtils`):
- **SA ID Number**: Validated via Luhn Algorithm.
- **Banking**: Regex-based validation for Account Numbers (7-13 digits) and Branch Codes (6 digits).
- **Currency Precision**: Use the `Money` domain class (BigDecimal). Never use `Double` for financial arithmetic. Use `amount.toPlainString()` when serializing for Supabase RPCs.

---

## 📂 6. Files & Locations

- **Models**: `domain/src/main/java/com/sanibonani/save/domain/model/`
- **Room Setup**: `data/src/main/java/com/sanibonani/save/data/local/`
- **Local DB Provider**: `data/src/main/java/com/sanibonani/save/data/local/LocalDatabaseProvider.kt`
- **Auth Data Source (SDK boundary)**: `data/src/main/java/com/sanibonani/save/data/remote/supabase/SupabaseAuthDataSourceImpl.kt`
- **Session mapping**: `data/src/main/java/com/sanibonani/save/data/datasource/AuthDataSource.kt`
- **Sync Core**: `data/src/main/java/com/sanibonani/save/data/repository/BaseRepository.kt`
- **Network Mapping**: `data/src/main/java/com/sanibonani/save/data/remote/PostgrestColumns.kt`
