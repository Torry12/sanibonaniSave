# AGENTS.md — SanibonaniSave AI Coding Reference

**Last Updated**: June 3, 2026  
**Version**: 3.0 (Consolidated Architecture & Operations Guide)

---

## 🚀 Quick Start (Read First)

### Project Identity
- **Product**: South African Savings Groups Administration Platform (Android + Kotlin + Jetpack Compose)
- **Vision**: Distributed Cooperative Financial Infrastructure supporting burial societies, stokvels, ROSCAs, investment clubs, and emergency funds
- **Backend**: Supabase (PostgreSQL + PostgREST + Realtime + Storage + Auth)
- **Offline-First**: Room SQLite cache with sync-on-reconnect pattern

### Module Boundaries (3-module architecture)
```
:app       (UI, ViewModels, DI, Workers, Services, Analytics)
  ├── depends on :domain + :data
:domain    (Models, Interfaces, UseCases, Services, Validation)
  ├── depends on nothing
:data      (Room entities/DAOs, Supabase implementations, Repositories)
  └── depends on :domain
```
See `settings.gradle.kts` and build files for enforcement.

### Core Data Flow
1. **Local First**: Repository emits Room cache data immediately
2. **Sync Network**: Simultaneously fetches from Supabase in background
3. **User Updates**: Observable via `StateFlow<T>` in UI
4. **Conflict Resolution**: `observeAndSync` pattern with exponential backoff retry

### Key Architectural Files
- **DI Wiring**: `app/src/main/java/com/sanibonani/save/di/AppModule.kt` (Supabase) + `RepoModule.kt` (repository bindings)
- **Repository Orchestration**: `data/src/main/java/com/sanibonani/save/data/repository/BaseRepository.kt` (observeAndSync)
- **Route/Role Enforcement**: `app/src/main/java/com/sanibonani/save/ui/navigation/NavGraph.kt`
- **Fee Enforcement**: `app/src/main/java/com/sanibonani/save/worker/FeeEnforcementWorker.kt` (24-hour periodic) + backend edge functions
- **Offline Migrations**: `app/src/main/java/com/sanibonani/save/data/local/Migrations.kt` (Room schema versioning)

---

## 🏛️ Core Engineering Principles

### 1. Immutable Financial Ledger (CRITICAL)
**NEVER directly mutate balances. Always derive from ledger entries.**

```kotlin
// ❌ WRONG
member.balance += amount

// ✅ CORRECT
val balance = ledger.filter { it.memberId == id }.sumOf { it.amount }
```

**Required financial entities**:
- `LedgerEntry` — immutable transaction record
- `Transaction` — financial movement
- `SettlementRecord` — reconciliation snapshot
- `ReconciliationRecord` — audit trail
- `AuditLog` — compliance tracking

**Every financial action must**:
- Be traceable (with full audit context)
- Be reversible (via compensating ledger entry)
- Support reconciliation (idempotent)
- Be UI-safe (no raw exceptions to users)

### 2. Event-Driven Architecture (Recommended)
**Avoid synchronous workflows. Use events for critical financial operations.**

```kotlin
// ✅ Emit events for:
DomainEvent.PaymentCompleted(paymentId)
DomainEvent.PaymentFailed(paymentId, reason)
DomainEvent.KycVerified(memberId)
DomainEvent.MemberJoined(groupId, memberId)
DomainEvent.ContributionPosted(memberId, amount)
DomainEvent.PayoutExecuted(payoutId)
DomainEvent.RiskFlagged(memberId, riskLevel)
```

**Auto-Registration Pattern**:
- `domain/src/main/java/com/sanibonani/save/domain/event/EventHandlerInitializer.kt` auto-injects & registers handlers via Dagger
- Domain Services (`compliance/service/`, `domain/service/`, etc.) listen to events and enforce business rules
- No tight coupling between services

### 3. Offline-First with Conflict Resolution
**Room cache is source of truth until network succeeds.**

```kotlin
// observeAndSync pattern (in BaseRepository.kt)
override fun observeItems(): Flow<Result<List<Item>>> = observeAndSync(
    dbFlow = db.itemDao().observeAll(),              // emit local first
    mapper = { it.toModel() },                       // Room entity → Domain model
    toEntity = { it.toEntity() },                    // Domain model → Room entity
    networkFetch = { supabase.postgrest["items"].select().decodeList() },
    cacheSync = { list -> db.itemDao().syncAll(list) },  // write network to local
    retryPolicy = exponentialBackoff()               // retry on transient failures
)
```

---

## 📊 Group Type Specific Business Logic

Each group type has specialized financial and operational rules embedded in use cases and domain services.

### 1. Burial Society (Funeral Insurance)
*Primary Goal: Cover funeral expenses for members and beneficiaries*

| Aspect | Behavior |
|--------|----------|
| **Premium Model** | Fixed monthly + risk-adjusted surcharge (age>65 beneficiaries) |
| **Waiting Periods** | Accidental death (immediate), Natural death (6 months), Suicide (12 months) |
| **Claim Logic** | Requires death cert + beneficiary ID; capped at fixed amount or contribution multiple |
| **Admin Fee** | High (actuarial modeling required) |
| **Use Case** | `CalculateViabilityUseCase` (requires actuarial data) |

### 2. Stokvel (Traditional Savings)
*Primary Goal: Periodic payouts for social events or lump-sum savings*

| Aspect | Behavior |
|--------|----------|
| **Contribution Model** | Fixed monthly (most common) or flexible |
| **Annual Payout Calculation** | `CalculateStokvelPayoutsUseCase` estimates year-end total |
| **Member Share** | Based on `total_paid` + scheduled future contributions |
| **Projection Logic** | `(group.balance + (expected_monthly_total * months_remaining))` |
| **Admin Fee** | Low |

### 3. ROSCA (Rotating Savings and Credit Association)
*Primary Goal: Peer-to-peer interest-free lending via rotation*

| Aspect | Behavior |
|--------|----------|
| **Rotation Logic** | Automated by `CalculateRoscaRotationUseCase`; sorted by join date |
| **Pot Total** | `group.monthly_contribution * member_count` |
| **Payout Timing** | Assigned specific month; visual timeline shown to admins/members |
| **Loans** | Not applicable (ROSCA is the "loan" mechanism) |
| **Admin Fee** | Low |

### 4. Investment Club
*Primary Goal: Wealth creation through pooled capital*

| Aspect | Behavior |
|--------|----------|
| **Valuation Model** | Net Asset Value (NAV) = `group.balance` |
| **Unit Price** | Initially 1.0 (R1/unit); fluctuates as `balance / total_contributions` |
| **Member Valuation** | Calculated by `CalculateInvestmentClubValuationUseCase` |
| **Equity Ownership** | Proportional; shows current market value vs. cost basis |
| **Waiting Period** | 12 months (typical lockup) |
| **Loans** | Restricted (collateral-based only) |
| **Admin Fee** | Medium |

### 5. Emergency Fund
*Primary Goal: Quick access to funds for unexpected life events*

| Aspect | Behavior |
|--------|----------|
| **Withdrawal Validation** | `ProcessEmergencyWithdrawalUseCase` enforces limits |
| **Withdrawal Cap** | Max 50% of available liquidity per request |
| **Eligibility** | `ACTIVE` status members only |
| **Loans** | Allowed (unsecured) |
| **Admin Fee** | Low |

---

## 🧠 Behavior Tracking & Fraud Detection

### System Components
1. **Domain Models** (`BehaviorTracking.kt`)
   - `MemberBehaviorTrack` — tracks metrics (payment, loan, tenure)
   - `FraudDetectionEvent` — audit trail for suspicious activities
   - `BehaviorAnalyticsSummary` — group-level analytics
   - Enums: `FraudRiskLevel`, `BehaviorStatus`

2. **Data Layer** (`BehaviorTrackingRepositoryImpl`)
   - DAOs: `MemberBehaviorTrackDao`, `FraudDetectionEventDao`, `BehaviorAnalyticsSummaryDao`
   - Syncs between Room (local) and Supabase (remote)

3. **Scoring Engine** (`BehaviorScoringUtils.kt`)
   - Behavior Score (0–100): 40% payments + 30% loans + 10% tenure
   - Fraud Score (0–100): weighted fraud indicators
   - Risk Level Determination: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`

### Key Metrics Tracked
- **Payment Metrics** (40% of score): on-time, late, overdue, consistency, streaks
- **Loan Metrics** (30% of score): completion rate, defaults, active loans, overdue count
- **Tenure** (10% of score): months in group, last activity, join date
- **Fraud Indicators**: duplicate transactions, velocity spikes, unusual patterns, multiple accounts, rapid disbursements

### ViewModel Usage
- `BehaviorTrackingViewModel` exposes reactive state via `StateFlow`
- UI operations: flag member, suspend, update risk level, review audit trail

---

## 💻 Tech Stack & Versions

| Component | Version | Notes |
|-----------|---------|-------|
| **Language** | Kotlin 2.1.0 | K2 compiler enabled |
| **Build System** | Gradle 8.11.1 | AGP 8.7.3, KSP 2.1.0-1.0.29 |
| **UI** | Jetpack Compose 2024.12.01 | Material 3 components |
| **DI** | Hilt 2.51.1 | `@HiltViewModel`, `@Inject constructor` |
| **Async/Reactive** | Coroutines + Flow | StateFlow preferred over LiveData |
| **Local DB** | Room 2.6.1 | SQLite with migrations, version 36+ |
| **Backend** | Supabase 3.1.4 | Auth, PostgREST, Realtime, Storage, Functions |
| **HTTP Client** | Ktor 3.0.1 | Network engine for data layer |
| **Image Loading** | Coil 2.6.0 | Async image loading with caching |
| **Maps** | OSMDroid 6.1.18 | OpenStreetMap rendering |
| **JSON Serialization** | kotlinx.serialization | `@Serializable` + `@SerialName("snake_case")` |
| **Parceling** | Parcelize (kotlin.android) | `@Parcelize` for navigation arguments |

---

## 🎨 Code Patterns To Follow

### ViewModels (16 Total)
```kotlin
@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val repository: FeatureRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(FeatureUiState())
    val state: StateFlow<FeatureUiState> = _state.asStateFlow()
    
    fun loadData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.getData()
                .onSuccess { data -> _state.update { it.copy(data = data, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage(), isLoading = false) } }
        }
    }
}

data class FeatureUiState(
    val data: List<Item> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

**Key Rules**:
- State updates via `_state.update { it.copy(...) }` (never direct assignment)
- Always include `isLoading` and `error` fields
- Use `viewModelScope.launch` for all coroutines
- Map exceptions via `.toUserMessage()` before UI

**Job Lifecycle Hardening** (from AppStructureAnalysis):
```kotlin
private var loadDataJob: Job? = null

fun loadData() {
    loadDataJob?.cancel()  // cancel previous job if still running
    loadDataJob = viewModelScope.launch {
        // fetch and emit
    }
}
```

### Repositories (20+ Implementations)
```kotlin
// Interface (domain layer)
interface FeatureRepository {
    fun observeItems(): Flow<Result<List<Item>>>
    suspend fun saveItem(item: Item): Result<Item>
}

// Implementation (data layer)
class FeatureRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase
) : FeatureRepository {
    
    override fun observeItems() = observeAndSync(
        dbFlow = db.featureDao().observeAll(),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = { supabase.postgrest["features"].select().decodeList() },
        cacheSync = { list -> db.featureDao().syncAll(list) }
    )
    
    override suspend fun saveItem(item: Item) = runCatching {
        supabase.postgrest["features"].upsert(item) {
            onConflict = "unique_column"
            select()
        }.decodeSingle<Item>()
    }
}
```

**Standard Bindings** (in `RepoModule.kt`):
```kotlin
@Binds
@Singleton
abstract fun bindFeatureRepository(impl: FeatureRepositoryImpl): FeatureRepository
```

### Domain Models
```kotlin
@Serializable
@Parcelize
data class Item(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id") val groupId: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    // ... other fields
) : Parcelable
```

**Requirements**:
- `@Serializable` for JSON serialization
- `@Parcelize` for navigation argument passing
- `@SerialName("snake_case")` for Supabase field mapping
- `@EncodeDefault(EncodeDefault.Mode.NEVER)` for null fields

### Flow Collection in Composables
```kotlin
// ✅ Correct — captures lifecycle
val state by viewModel.state.collectAsState()

// ❌ Wrong — ignores lifecycle
val state = viewModel.state.value

// ✅ Advanced — custom lifecycle
LaunchedEffect(viewModel, lifecycleOwner) {
    viewModel.state.collect { newState ->
        // handle state update
    }
}
```

### Error Handling (No Raw Exceptions)
```kotlin
// ✅ Correct
_state.update { it.copy(error = e.toUserMessage()) }

// ❌ Wrong
_state.update { it.copy(error = e.message) }

// ❌ Wrong (silent failure)
val item = items.first() ?: return  // user gets no feedback
```

**Use `.toUserMessage()` extension** (`data/utils/SafeResultExtensions.kt`):
```kotlin
fun Throwable.toUserMessage(): String = when (this) {
    is HttpRequestTimeoutException -> "Network timeout. Please check your connection."
    is SupabaseException -> "Server error. Please try again later."
    else -> "An unexpected error occurred. Please try again."
}
```

### Room Migrations
```kotlin
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.safeExec("ALTER TABLE members ADD COLUMN new_field TEXT")
    }
}

// In SanibonaniDatabase.kt
database.addMigrations(
    MIGRATION_35_36,  // add to ALL_MIGRATIONS
    MIGRATION_36_37
)
```

**Rules**:
- Bump database version in `SanibonaniDatabase.kt`
- Use `db.safeExec()` for ALTER TABLE (catches missing columns gracefully)
- Always add new migration to `ALL_MIGRATIONS` array
- Keep SQL idempotent (duplicate migrations should not error)

### Use Cases (Domain Orchestration)
```kotlin
class GetGroupBusinessInsightsUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository
) {
    suspend operator fun invoke(groupId: String): Result<GroupInsights> = runCatching {
        val group = groupRepository.getGroup(groupId).getOrThrow()
        val members = memberRepository.getMembers(groupId).getOrThrow()
        
        // Orchestrate group-type-specific logic
        return when (group.type) {
            GroupType.BURIAL_SOCIETY -> calculateBurialSocietyInsights(group, members)
            GroupType.STOKVEL -> calculateStokvelInsights(group, members)
            GroupType.ROSCA -> calculateRoscaInsights(group, members)
            // ...
        }
    }
}
```

### Supabase Writes (Idempotent)
```kotlin
// ✅ Use upsert (safe for duplicates)
supabase.postgrest["items"].upsert(item) {
    onConflict = "unique_id,group_id"  // specify conflict columns
    select()
}.decodeSingle<Item>()

// ❌ Avoid insert (risks duplicate key errors)
supabase.postgrest["items"].insert(item)
```

### Background Workers
```kotlin
@HiltWorker
class FeeEnforcementWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val feeRepository: FeeRepository
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result = runCatching {
        feeRepository.enforceAllFees()
        Result.success()
    }.getOrElse {
        Result.retry()  // exponential backoff
    }
}

// Register in App.kt / WorkManager setup
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "fee_enforcement",
    ExistingPeriodicWorkPolicy.KEEP,
    PeriodicWorkRequestBuilder<FeeEnforcementWorker>(
        24.hours  // runs every 24 hours
    ).build()
)
```

### Domain Services (Stateless, Rule Enforcement)
13 service interfaces in `domain/service/`:
- `ComplianceService` — regulatory rules
- `FraudDetectionService` — anomaly detection
- `GovernanceService` — voting, quorum, consensus
- `KycService` — identity verification
- `ReconciliationService` — ledger balancing
- `RiskService` — portfolio/member risk scoring
- `TreasuryService` — fund management

```kotlin
interface ComplianceService {
    suspend fun validateContribution(memberId: String, amount: BigDecimal): Result<Unit>
    suspend fun checkMaxLoanExposure(memberId: String): Result<Boolean>
}
```

### Cache Services (Context-Specific State)
Singleton services managing runtime context:
- `AdminGroupContextCacheService` — admin's current group context
- `MemberGroupContextCacheService` — member's current group context
- `UserProfileCacheService` — logged-in user profile cache

```kotlin
@Singleton
class AdminGroupContextCacheService @Inject constructor(
    private val groupRepository: GroupRepository
) {
    private val _currentGroup = MutableStateFlow<Group?>(null)
    val currentGroup: StateFlow<Group?> = _currentGroup.asStateFlow()
    
    suspend fun setContext(groupId: String) {
        _currentGroup.value = groupRepository.getGroup(groupId).getOrNull()
    }
}
```

### Analytics & Event Logging
- `app/src/main/java/com/sanibonani/save/analytics/` module
- Log user behavior, financial transactions, system events
- Dashboard integration via event streaming

---

## 🧪 Critical Commands (PowerShell)

### Build & Debug
```powershell
# Debug APK
./gradlew.bat :app:assembleDebug

# Run on device/emulator
./gradlew.bat :app:installDebug

# Build with verification
./gradlew.bat :app:assembleDebug --scan
```

### Testing
```powershell
# JVM unit tests
./gradlew.bat test

# Instrumentation tests (requires device/emulator)
./gradlew.bat :app:connectedDebugAndroidTest

# Specific test class
./gradlew.bat test --tests*GroupViewModelTest
```

### Navigation & Role Validation
```powershell
# Validate NavGraph role transitions
./run-navgraph-role-transition-test.ps1 -CheckOnly

# Full role transition simulation
./run-navgraph-role-transition-test.ps1
```

### Runtime Signal (CI/CD Diagnostics)
```powershell
# Unified diagnostic probe
./scripts/runtime-signal-unified.ps1

# With local function checks
./scripts/runtime-signal-unified.ps1 -CheckLocalFunction -Strict

# With retries
./scripts/runtime-signal-unified.ps1 -CheckLocalFunction -Retry 2

# Custom timeout
./scripts/runtime-signal-unified.ps1 -CheckLocalFunction -TimeoutSeconds 15
```

### Device Setup Helper
```powershell
# Build, install, and launch on device
./scripts/run-android-debug.ps1
```

---

## ✅ Safe-Change Checklist

### Database Migrations
- [ ] SQL edits: keep `supabase/migrations/` aligned with `/supabase/` top-level scripts
- [ ] Room edits: bump version in `SanibonaniDatabase.kt` + add to `ALL_MIGRATIONS` array
- [ ] Never rely on destructive migrations on release
- [ ] Idempotent SQL: migrations must be re-runnable

### Workers & Background Tasks
- [ ] Use `@HiltWorker` + `CoroutineWorker` annotations
- [ ] Register in WorkManager (via `App.kt` or DI module)
- [ ] Set appropriate frequency (24h for fees, 1d for probation, etc.)
- [ ] Return `Result.success()` or `Result.retry()` (exponential backoff)

### Row-Level Security (RLS)
- [ ] Validate role paths in `NavGraph.kt` before merging
- [ ] Cross-check repository filters match RLS policies
- [ ] Test with test accounts for each role (platform admin, group admin, member)

### Test Data Seeding
- [ ] Preserve idempotence markers (`SEED-%`)
- [ ] Use targeted cleanup behavior (see `supabase/11_SEED_TEST_DATA_10_GROUPS_100_MEMBERS.sql`)
- [ ] Never rely on seed scripts for production data

### Edge Functions
- [ ] Keep app callers in `data/remote/EdgeFunctionGateway.kt` compatible with remote payload/response
- [ ] Document schema changes (request body + response format)
- [ ] Test with timeout handling (default 8s, configurable via runtime-signal)

### Fresh Backend Setup (Order Matters)
1. `supabase/01_DATABASE_SCHEMA.sql` — tables, indexes, sequences
2. `supabase/02_SECURITY_AND_RLS.sql` — RLS policies
3. `supabase/03_PLATFORM_ADMIN_SETUP.sql` — platform bootstrap
4. `supabase/29_ALIGN_VALIDATION_CONSTRAINTS_WITH_APP.sql` — app alignment (if existing env)
5. Optional: apply hotfix/alignment scripts as needed

---

## 📁 Architecture Extensions

### Repository Pattern (20+ implementations)
**Location**: `data/src/main/java/com/sanibonani/save/data/repository/`

**Core Repositories**:
- LedgerRepository — financial transactions
- GroupRepository — group CRUD + state
- MemberRepository — member CRUD + status transitions
- PaymentRepository — contribution tracking
- LoanRepository — loan lifecycle
- VotingRepository — group polls + decisions
- ActuarialRepository — viability calculations
- BeneficiaryClaimRepository — claim processing
- BehaviorTrackingRepository — member behavior metrics
- NotificationRepository — SMS/email/push messaging
- PaymentGatewayRepository — YoCo payment integration
- StorageRepository — file uploads (constitutions, IDs, etc.)
- SyncRepository — offline sync status tracking
- PlatformConfigRepository — platform-wide settings

**Bindings** (in `RepoModule.kt`):
All repository implementations are bound to their interfaces via `@Binds` and `@Singleton`.

### Use Case Layer (Domain Orchestration)
**Location**: `domain/src/main/java/com/sanibonani/save/domain/usecase/`

**Key Use Cases**:
- Voting: `CreateGroupPollUseCase`, `CastGroupPollVoteUseCase`
- Actuarial: `CalculateViabilityUseCase`, `ApplyViabilityPlanUseCase`
- Stokvel: `CalculateStokvelPayoutsUseCase`
- ROSCA: `CalculateRoscaRotationUseCase`
- Investment: `CalculateInvestmentClubValuationUseCase`
- Groups: `GetGroupBusinessInsightsUseCase`
- Documents: `VerifyRelationalDocumentUseCase`

**Pattern**:
```kotlin
class MyUseCase @Inject constructor(
    private val repo1: Repository1,
    private val repo2: Repository2,
    private val service: DomainService
) {
    suspend operator fun invoke(input: InputType): Result<OutputType> = runCatching {
        // multi-step orchestration
        val data1 = repo1.fetch(id).getOrThrow()
        val data2 = repo2.process(data1).getOrThrow()
        return service.validate(data2)  // business rule enforcement
    }
}
```

### Workers (Background Tasks)
**Location**: `app/src/main/java/com/sanibonani/save/worker/`

- **FeeEnforcementWorker** — 24-hour periodic task; applies late fees to overdue contributions
- **ProbationCompletionWorker** — daily task; promotes members from probation to active

**Pattern**:
```kotlin
@HiltWorker
class MyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MyRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            repository.performBackgroundTask()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
```

### Domain Services (Stateless Rule Enforcement)
**Location**: `domain/src/main/java/com/sanibonani/save/domain/service/`

13 service interfaces:
- `ComplianceService` — regulatory constraint validation
- `FraudDetectionService` — anomaly scoring and flagging
- `GovernanceService` — voting quorum, role hierarchy, delegation (in-process orchestrator)
- `InMemoryGovernanceService` (impl) — handles group voting mechanics
- `KycService` — identity verification workflows
- `ReconciliationService` — ledger balancing checks
- `RiskService` — member/portfolio risk scoring
- `TreasuryService` — fund allocation and reserve management

**Pattern**:
```kotlin
interface ComplianceService {
    suspend fun validateContribution(memberId: String, amount: BigDecimal): Result<Unit>
    suspend fun checkMaxLoanExposure(memberId: String): Result<Boolean>
}
```

### Analytics Module
**Location**: `app/src/main/java/com/sanibonani/save/analytics/`

- Event logging for user behavior
- Financial transaction tracking
- Fraud/risk event streaming
- Dashboard feed aggregation

### Event Orchestration (Auto-Registration)
**Location**: `domain/src/main/java/com/sanibonani/save/domain/event/EventHandlerInitializer.kt`

**Pattern**:
```kotlin
// EventHandlerInitializer auto-injects and registers all domain event listeners
// No manual registration needed — Dagger discovers all @DomainEventListener implementations

@Singleton
class EventHandlerInitializer @Inject constructor(
    private val handlers: Set<@JvmSuppressWildcards DomainEventListener>
) {
    init {
        // Dagger automatically collects all event listeners
        handlers.forEach { it.registerListeners() }
    }
}
```

---

## 🔐 Environment & Secrets

### Local Properties (Required)
Copy `local.properties.template` → `local.properties` and fill in:

```properties
# Supabase Backend
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=eyJhbGci...
SUPABASE_SERVICE_ROLE_KEY=eyJhbGci...  (server-side only, not in app BuildConfig)

# Payment Gateway (YoCo)
YOCO_PUBLIC_KEY=pk_live_...
YOCO_WEBHOOK_SECRET=whsec_...

# Address Autocomplete (Geoapify)
GEOAPIFY_API_KEY=your_api_key

# WhatsApp (Server-Side Only — Edge Function Secret)
WHATSAPP_TOKEN=your_meta_token (NOT in app)
WHATSAPP_PHONE_NUMBER_ID=your_phone_id (NOT in app)
```

### Firebase Configuration
- Download `google-services.json` from Firebase Console
- Place in `app/` directory
- Required for FCM push notifications

### GitHub Secrets (CI/CD)
Set in repository settings for automated deployments:
- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `FIREBASE_CREDENTIALS`
- `PLAY_STORE_KEY`

---

## 🎯 Quick Reference: Common Patterns

### I need to add a new feature...

1. **Define domain model** (`domain/model/Models.kt`)
   ```kotlin
   @Serializable @Parcelize
   data class Feature(...) : Parcelable
   ```

2. **Add Room entity + DAO** (`data/local/`)
   ```kotlin
   @Entity("features")
   data class FeatureEntity(...)
   
   @Dao
   interface FeatureDao { fun getAll(): Flow<List<FeatureEntity>> }
   ```

3. **Define repository interface** (`domain/repository/`)
   ```kotlin
   interface FeatureRepository {
       fun observeAll(): Flow<Result<List<Feature>>>
   }
   ```

4. **Implement repository** (`data/repository/`)
   ```kotlin
   class FeatureRepositoryImpl @Inject constructor(...) : FeatureRepository {
       override fun observeAll() = observeAndSync(...) { ... }
   }
   ```

5. **Bind in DI** (`app/di/RepoModule.kt`)
   ```kotlin
   @Binds @Singleton
   abstract fun bindFeatureRepository(impl: FeatureRepositoryImpl): FeatureRepository
   ```

6. **Create ViewModel** (`app/viewmodel/`)
   ```kotlin
   @HiltViewModel
   class FeatureViewModel @Inject constructor(private val repo: FeatureRepository) : ViewModel() { ... }
   ```

7. **Create Compose screens** (`app/ui/screens/`)
   ```kotlin
   @Composable
   fun FeatureScreen(viewModel: FeatureViewModel = hiltViewModel()) { ... }
   ```

### I need to enforce a business rule...

1. **Create domain service interface** (`domain/service/`)
   ```kotlin
   interface MyRuleService {
       fun validate(input: Data): Result<Unit>
   }
   ```

2. **Implement service** (bind via `@Provides` in `AppModule.kt`)
   ```kotlin
   @Singleton
   @Provides
   fun provideMyRuleService(...): MyRuleService = MyRuleServiceImpl(...)
   ```

3. **Inject into ViewModel or UseCase**
   ```kotlin
   class MyUseCase @Inject constructor(private val service: MyRuleService) { ... }
   ```

4. **Emit domain event after rule success**
   ```kotlin
   EventBus.emit(DomainEvent.BusinessRuleApplied(...))
   ```

### I need to add a background task...

1. **Create @HiltWorker**
   ```kotlin
   @HiltWorker
   class MyWorker @AssistedInject constructor(
       @Assisted context: Context,
       @Assisted params: WorkerParameters,
       private val repository: MyRepository
   ) : CoroutineWorker(context, params) { ... }
   ```

2. **Register in WorkManager** (App.kt or DI module)
   ```kotlin
   WorkManager.getInstance(context).enqueueUniquePeriodicWork(
       "my_task", ExistingPeriodicWorkPolicy.KEEP,
       PeriodicWorkRequestBuilder<MyWorker>(duration).build()
   )
   ```

### I need to sync data from Supabase...

Use `observeAndSync` pattern in repository:
```kotlin
override fun observeItems() = observeAndSync(
    dbFlow = db.itemDao().observeAll(),
    mapper = { it.toModel() },
    toEntity = { it.toEntity() },
    networkFetch = { supabase.postgrest["items"].select().decodeList() },
    cacheSync = { list -> db.itemDao().syncAll(list) }
)
```

---

## 📚 Related Documentation

- **APP_ARCHITECTURE_AND_TECHNICAL_GUIDE.md** — Detailed tech stack, setup, coding standards
- **sanibonanisave_architecture_guidance_context.md** — Platform vision, distributed finance principles
- **GROUP_TYPES_LOGIC.md** — Burial, stokvel, ROSCA, investment, emergency fund rules
- **BEHAVIOR_TRACKING_SYSTEM.md** — Fraud detection, behavior metrics, scoring algorithms
- **.github/copilot-instructions.md** — Copilot-specific code generation rules
- **.cursorrules** — Cursor IDE-specific rules
- **README.md** — Quick setup, project structure overview
- **FINAL_SUMMARY_RUNTIME_V2.md** — CI/CD runtime signal v2.0 delivery details

---

**Questions or issues?** Check the documentation index in `docs/INDEX.md` or examine the test accounts in the QA_LOGIN_CHECKLIST.md.
