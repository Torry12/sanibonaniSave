# GitHub Copilot Instructions for SanibonaniSave

## Project Context
You are working on **SanibonaniSave**, a South African savings groups administration platform built with Kotlin and Jetpack Compose.

## Code Generation Rules

### Always Use
- `StateFlow` and `SharedFlow` for reactive state (NEVER `LiveData`)
- `@HiltViewModel` annotation for ViewModels
- `@Inject constructor` for dependency injection
- `runCatching` or `Result<T>` for error handling
- `viewModelScope.launch` for coroutines in ViewModels
- `@Serializable` + `@Parcelize` for data classes
- `@SerialName("snake_case")` for JSON field mapping

### Never Use
- `LiveData` or `MutableLiveData`
- Manual instantiation of repositories/use cases
- Android `Context` in ViewModels
- Business logic in Composables
- Raw exception messages in UI

### State Updates
```kotlin
// ✅ Correct
_state.update { it.copy(isLoading = true) }

// ❌ Wrong
_state.value = _state.value.copy(isLoading = true)
```

### Flow Collection in Composables
```kotlin
// ✅ Correct
val state by viewModel.state.collectAsState()

// ❌ Wrong  
val state = viewModel.state.value
```

### Repository Operations
```kotlin
// ✅ Use upsert for potential duplicates
supabase.postgrest["table"].upsert(item) {
    onConflict = "unique_column"
    select()
}

// ❌ Avoid insert without conflict handling
supabase.postgrest["table"].insert(item)
```

### Error Handling
```kotlin
// ✅ Correct - User-friendly message
_state.update { it.copy(error = e.toUserMessage()) }

// ❌ Wrong - Raw exception
_state.update { it.copy(error = e.message) }
```

### Null Safety with Feedback
```kotlin
// ✅ Correct - User feedback on null
val plan = state.value.viabilityPlan
if (plan == null) {
    _state.update { it.copy(error = "Please calculate strategy first.") }
    return
}

// ❌ Wrong - Silent return
val plan = state.value.viabilityPlan ?: return
```

## File Templates

### New ViewModel
```kotlin
@HiltViewModel
class NewFeatureViewModel @Inject constructor(
    private val repository: FeatureRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(NewFeatureUiState())
    val state: StateFlow<NewFeatureUiState> = _state.asStateFlow()
    
    fun loadData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.getData()
                .onSuccess { _state.update { it.copy(data = it, isLoading = false) } }
                .onFailure { e -> _state.update { it.copy(error = e.toUserMessage(), isLoading = false) } }
        }
    }
}

data class NewFeatureUiState(
    val data: List<Item> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

### New Repository
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
        supabase.postgrest["features"].upsert(item) { select() }.decodeSingle<Item>()
    }
}
```

### New Migration
```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(db: SupportSQLiteDatabase) {
        fun safeExec(sql: String) {
            try { db.execSQL(sql) } catch (e: Exception) {}
        }
        safeExec("ALTER TABLE table_name ADD COLUMN new_column TYPE")
    }
}

// Don't forget to add to ALL_MIGRATIONS array!
```

## Domain Model Pattern
```kotlin
@Serializable
@Parcelize
data class NewModel(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: String? = null,
    @SerialName("group_id") val groupId: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    // ... other fields
) : Parcelable
```

## Common Import Block
```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
```

