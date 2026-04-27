# AI_RULES.md — SanibonaniSave Coding Standards

> **Purpose**: This file defines strict coding rules for AI agents working on this codebase.

---

## 🚫 FORBIDDEN PATTERNS

### 1. LiveData
```kotlin
// ❌ NEVER USE
val data = MutableLiveData<List<Item>>()
fun observe() = data.observe(lifecycleOwner) { }

// ✅ ALWAYS USE
val data = MutableStateFlow<List<Item>>(emptyList())
fun observe() = data.asStateFlow()
```

### 2. Manual DI
```kotlin
// ❌ NEVER USE
val repo = RepositoryImpl(SupabaseClient(), Database())

// ✅ ALWAYS USE
@Inject constructor(private val repo: Repository)
```

### 3. Context in ViewModel
```kotlin
// ❌ NEVER USE
class MyViewModel(private val context: Context)

// ✅ ALWAYS USE
class MyViewModel @Inject constructor(private val repo: Repository)
```

### 4. Business Logic in Composables
```kotlin
// ❌ NEVER USE
@Composable
fun Screen() {
    val result = calculateTax(amount) // Business logic!
}

// ✅ ALWAYS USE
@Composable
fun Screen(vm: ViewModel) {
    val state by vm.state.collectAsState()
    Text(state.calculatedTax)
}
```

### 5. Silent Null Returns
```kotlin
// ❌ NEVER USE
fun doSomething() {
    val item = state.item ?: return  // User gets no feedback
}

// ✅ ALWAYS USE
fun doSomething() {
    val item = state.item
    if (item == null) {
        _state.update { it.copy(error = "Please select an item first.") }
        return
    }
}
```

### 6. Raw SQL Insert (when unique constraint exists)
```kotlin
// ❌ NEVER USE (causes duplicate key errors)
supabase.postgrest["table"].insert(item)

// ✅ ALWAYS USE
supabase.postgrest["table"].upsert(item) {
    onConflict = "unique_column"
    select()
}
```

### 7. Silent Payment Type Fallback
```kotlin
// ❌ NEVER USE
val paymentType = when (type) {
    "joining_fee" -> PaymentType.JOINING_FEE
    else -> PaymentType.CONTRIBUTION
}

// ✅ ALWAYS USE
val paymentType = when (type) {
    "registration", "admin_fee", "platform_fee" -> PaymentType.PLATFORM_FEE
    "joining_fee" -> PaymentType.JOINING_FEE
    "contribution" -> PaymentType.CONTRIBUTION
    else -> return _state.update { it.copy(error = "Unsupported payment type. Please retry.") }
}
```

---

## ✅ REQUIRED PATTERNS

### ViewModel State Management
```kotlin
@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val repository: FeatureRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FeatureUiState())
    val state: StateFlow<FeatureUiState> = _state.asStateFlow()

    fun performAction() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            repository.action()
                .onSuccess { result ->
                    _state.update { it.copy(
                        data = result,
                        isLoading = false,
                        successMessage = "Action completed"
                    ) }
                }
                .onFailure { e ->
                    _state.update { it.copy(
                        isLoading = false,
                        error = e.toUserMessage()
                    ) }
                }
        }
    }
}
```

### Repository Implementation
```kotlin
class FeatureRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase
) : FeatureRepository {

    override fun observeData(id: String): Flow<Result<List<Item>>> = observeAndSync(
        dbFlow = db.itemDao().observe(id),
        mapper = { entity -> entity.toModel() },
        toEntity = { model -> model.toEntity() },
        networkFetch = {
            supabase.postgrest["items"]
                .select { filter { eq("parent_id", id) } }
                .decodeList<Item>()
        },
        cacheSync = { list -> db.itemDao().sync(id, list) }
    )

    override suspend fun saveItem(item: Item): Result<Item> = runCatching {
        val saved = supabase.postgrest["items"].upsert(item) {
            onConflict = "id"
            select()
        }.decodeSingle<Item>()
        db.itemDao().upsert(saved.toEntity())
        saved
    }
}
```

### Contribution RPC Contract
```kotlin
// ✅ RPC contract invariant
// p_type -> contributions.type (business meaning)
// payment_method remains transport metadata (e.g. "yoco")
supabase.postgrest.rpc("record_contribution_v1", buildJsonObject {
    put("p_member_id", contribution.memberId)
    put("p_group_id", contribution.groupId)
    put("p_amount", contribution.amount)
    put("p_type", contribution.type)
})
```

### Domain Model
```kotlin
@Serializable
@Parcelize
data class Item(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    
    @SerialName("group_id")
    val groupId: String = "",
    
    @SerialName("display_name")
    val displayName: String = "",
    
    val status: ItemStatus = ItemStatus.PENDING,
    
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("created_at")
    val createdAt: String? = null
) : Parcelable

@Serializable
enum class ItemStatus {
    @SerialName("pending") PENDING,
    @SerialName("active") ACTIVE,
    @SerialName("inactive") INACTIVE
}
```

### Room Migration
```kotlin
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        fun safeExec(sql: String) {
            try { db.execSQL(sql) } catch (e: Exception) { /* ignore */ }
        }
        
        // Add new columns safely
        safeExec("ALTER TABLE items ADD COLUMN new_field TEXT")
        safeExec("ALTER TABLE items ADD COLUMN another_field INTEGER NOT NULL DEFAULT 0")
        
        // Create new table if needed
        safeExec("""
            CREATE TABLE IF NOT EXISTS new_table (
                id TEXT PRIMARY KEY NOT NULL,
                parent_id TEXT NOT NULL,
                name TEXT NOT NULL
            )
        """)
        safeExec("CREATE INDEX IF NOT EXISTS idx_new_table_parent ON new_table(parent_id)")
    }
}

// ⚠️ CRITICAL: Add to ALL_MIGRATIONS array!
val ALL_MIGRATIONS = arrayOf(
    // ... existing migrations ...
    MIGRATION_32_33
)
```

### Composable Screen
```kotlin
@Composable
fun FeatureScreen(
    onNavigateBack: () -> Unit,
    vm: FeatureViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSuccessMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { /* ... */ }
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator()
            state.error != null -> ErrorCard(state.error, onRetry = { vm.retry() })
            else -> FeatureContent(state, vm)
        }
    }
}
```

---

## 📁 FILE NAMING

| Type | Pattern | Example |
|------|---------|---------|
| ViewModel | `{Feature}ViewModel` | `MemberViewModel` |
| UiState | `{Feature}UiState` | `MemberUiState` |
| Screen | `{Feature}Screen` | `MemberDashboardScreen` |
| Repository Interface | `{Feature}Repository` | `MemberRepository` |
| Repository Impl | `{Feature}RepositoryImpl` | `MemberRepositoryImpl` |
| UseCase | `{Action}{Feature}UseCase` | `RegisterMemberUseCase` |
| Entity | `{Feature}Entity` | `MemberEntity` |
| Model | `{Feature}` | `Member` |

---

## 🔒 SECURITY RULES

1. **Never log passwords or tokens**
2. **Use EncryptedSharedPreferences for credentials**
3. **Include Bearer token in storage bucket requests**
4. **Cast UUID to TEXT in RLS policies**: `m.id::text = ...`
5. **Validate file sizes before upload** (max 3MB)

---

## 🧪 TESTING CHECKLIST

Before submitting code:
- [ ] No `LiveData` usage
- [ ] All DI via Hilt `@Inject`
- [ ] ViewModels don't reference Context
- [ ] Repository uses `Result<T>` wrapper
- [ ] Migrations added to `ALL_MIGRATIONS`
- [ ] Database version bumped if schema changed
- [ ] User-friendly error messages (not raw exceptions)
- [ ] Upsert used where unique constraints exist

