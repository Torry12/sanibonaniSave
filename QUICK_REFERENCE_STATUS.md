# SanibonaniSave — Quick Reference & Status Report
**April 1, 2026**

---

## 🎯 **APP STATUS: READY FOR TESTING**

### Critical Fixes Applied (This Session)
```
✅ Fixed: Enum Serialization Bug (4 instances)
   - GroupRepository.kt lines 234, 240, 257
   - MemberRepository.kt line 148
   
   Changed FROM: put("fee_status", AdminFeeState.PAID.name.lowercase())
   Changed TO:   put("fee_status", AdminFeeState.PAID)
   
   Result: Enums now serialize correctly via @SerialName annotations
```

---

## 📋 **APP ARCHITECTURE AT A GLANCE**

```
┌─────────────────────────────────────────────────────────────┐
│                      JETPACK COMPOSE UI                      │
│  (Landing, Auth, Browse, Group, Member, Admin, Payment)     │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   HILT VIEWMODELS (Flow)                     │
│  AuthVM, GroupVM, MemberVM, AdminVM, PolicyVM, PaymentVM    │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│            REPOSITORIES (Result<T> Pattern)                  │
│  Group, Member, Payment, Notification, Actuarial, Investment│
│                   ↓              ↓                           │
│    ┌─────────────────┐  ┌──────────────────┐               │
│    │ Supabase (API)  │  │ Room Database    │               │
│    │ - PostgREST     │  │ - Offline cache  │               │
│    │ - Auth          │  │ - Fast access    │               │
│    │ - Realtime      │  │ - Always synced  │               │
│    │ - Storage       │  │                  │               │
│    │ - Functions     │  │                  │               │
│    └─────────────────┘  └──────────────────┘               │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔑 **KEY PATTERNS IN USE**

### 1. **Flow-Based State Management**
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(repo: MyRepository) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    
    fun updateField(value: String) {
        _state.update { it.copy(field = value) }  // Immutable
    }
}
```

### 2. **Repository Result Pattern**
```kotlin
interface MyRepository {
    suspend fun getData(id: String): Result<Data>
    fun observeData(id: String): Flow<Result<List<Data>>>
}

// Usage in ViewModel:
repo.getData(id)
    .onSuccess { data -> _state.update { it.copy(data = data) } }
    .onFailure { e -> _state.update { it.copy(error = e.message) } }
```

### 3. **Offline-First Sync**
```kotlin
override fun getPublicGroups(): Flow<Result<List<Group>>> = observeWithCache(
    dbFlow = db.groupDao().observePublicGroups(),
    mapper = { it.toModel() },
    networkFetch = { supabase.postgrest["groups"].select(...).decodeList<Group>() },
    cacheUpdate = { list -> db.groupDao().upsertGroups(list.map { it.toEntity() }) }
)
```

### 4. **Enum Serialization (CRITICAL)**
```kotlin
// ✅ CORRECT: @SerialName handles serialization
@Serializable
enum class AdminFeeState {
    @SerialName("paid")     PAID,
    @SerialName("due")      DUE,
    @SerialName("warning")  WARNING
}

// ❌ WRONG: Don't do this anymore
put("fee_status", AdminFeeState.PAID.name.lowercase())  // REMOVED

// ✅ CORRECT: Let Supabase client serialize
put("fee_status", AdminFeeState.PAID)
```

### 5. **Entity Mapping**
```kotlin
// Model ↔ Entity conversions (in Mappers.kt)
fun Group.toEntity() = GroupEntity(...)  // ViewModel → DB
fun GroupEntity.toModel() = Group(...)   // DB → ViewModel
```

### 6. **Hilt Dependency Injection**

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideGroupRepository(...): GroupRepository = GroupRepositoryImpl(...)
}

// In ViewModel:
@HiltViewModel
class MyVM @Inject constructor(private val repo: GroupRepository) : ViewModel() 
```

---

## 📊 **DATA MODEL QUICK REFERENCE**

### Core Models
```kotlin
Group             // Burial society, stokvel, etc.
  ├─ id, name, type, province, city, township
  ├─ joinFee, monthlyContribution, lateFee
  ├─ currentMembers, maxMembers, balance
  ├─ feeStatus (AdminFeeState) ← NOW SERIALIZES CORRECTLY
  ├─ adminUserId, registrationPaid
  └─ createdAt, updatedAt

Member            // User membership in a group
  ├─ id, userId, groupId, memberKey
  ├─ fullName, idNumber, phone, email
  ├─ status (MemberStatus) ← NOW SERIALIZES CORRECTLY
  ├─ joinedAt, probationEndAt
  ├─ document1/2 (URL, type, status)
  └─ totalContributions, fcmToken

Contribution      // Monthly payment record
  ├─ id, memberId, groupId
  ├─ amount, dueDate, paidAt
  ├─ status (ContributionStatus)
  └─ lateFeesApplied

Payment           // Payment transaction record
  ├─ id, memberId, groupId
  ├─ amount, paymentType, paymentMethod
  ├─ transactionId (YoCo), status
  └─ processedAt, createdAt

PlatformFee       // Monthly platform fee tracking
  ├─ id, groupId
  ├─ feeType (registration, monthly)
  ├─ amount, status (AdminFeeState)
  └─ memberCount, ratePerMember
```

---

## 🚀 **QUICK START FOR DEVELOPERS**

### Setting Up Local Dev
```bash
# 1. Clone repo
git clone <repo-url>
cd SanibonaniSave_Full

# 2. Configure secrets (local.properties)
# Copy local.properties.template and fill in:
SUPABASE_URL=https://...
SUPABASE_ANON_KEY=...
YOCO_PUBLIC_KEY=...
# etc.

# 3. Build and run
./gradlew clean assembleDebug
# Or in Android Studio: Run → Run 'app'

# 4. First launch
# - Splash screen checks Supabase connection
# - If successful, shows Landing page
# - If failed, shows Connection Error screen
```

### Adding a New Feature
```
1. Define data model (Models.kt)
   - Add @Serializable + @Parcelize
   - Use @SerialName for all fields
   - Add enum @SerialName annotations

2. Create Room entity (SanibonaniDatabase.kt)
   - Add @Entity class + @Dao interface
   - Create toEntity()/toModel() mappers

3. Create repository interface & impl
   - Use Result<T> pattern
   - Inject DB + Supabase client
   - Implement observeWithCache() for reads

4. Create ViewModel
   - Use MutableStateFlow for state
   - Call repo via viewModelScope.launch
   - Map errors to user messages

5. Create Composable screen
   - Collect state via .collectAsState()
   - No repository access (use ViewModel only)
   - Handle loading/error states

6. Add to NavGraph
   - Create Screen object
   - Add composable() entry
   - Update navigation logic
```

---

## 🧪 **COMMON OPERATIONS**

### View User Payment History
```kotlin
// MemberViewModel
private val _payments = MutableStateFlow<List<Payment>>(emptyList())
val payments: StateFlow<List<Payment>> = _payments.asStateFlow()

fun loadPayments(memberId: String) {
    viewModelScope.launch {
        paymentRepo.getMemberPaymentHistory(memberId)
            .collect { result ->
                result.onSuccess { payments ->
                    _payments.value = payments
                }.onFailure { error ->
                    // Handle error
                }
            }
    }
}
```

### Record Member Payment
```kotlin
// PaymentViewModel
fun recordPayment(groupId: String, amount: Double, type: PaymentType) {
    viewModelScope.launch {
        val payment = Payment(
            memberId = currentUserId,
            groupId = groupId,
            amount = amount,
            paymentType = type,
            paymentMethod = PaymentMethod.YOCO,
            status = PaymentStatus.PENDING
        )
        
        paymentRepo.recordPayment(payment)
            .onSuccess { paymentId ->
                // Update group balance
                groupRepo.incrementGroupBalance(groupId, amount)
            }
            .onFailure { error ->
                _state.update { it.copy(error = error.message) }
            }
    }
}
```

### Update Member Status
```kotlin
// AdminViewModel
fun suspendMember(memberId: String) {
    viewModelScope.launch {
        memberRepo.updateMemberStatus(memberId, MemberStatus.SUSPENDED)
            .onSuccess {
                // Send notification
                notificationRepo.sendNotification(...)
            }
            .onFailure { error ->
                _state.update { it.copy(error = error.message) }
            }
    }
}
```

### Create Group with Admin
```kotlin
// GroupViewModel
fun createGroup(group: Group, adminEmail: String, adminPassword: String) {
    viewModelScope.launch {
        _state.update { it.copy(isSubmitting = true) }
        
        groupRepo.createGroup(group, adminEmail, adminPassword)
            .onSuccess { groupId ->
                _state.update { 
                    it.copy(
                        isSubmitting = false,
                        success = true,
                        createdGroupId = groupId
                    ) 
                }
            }
            .onFailure { error ->
                _state.update { 
                    it.copy(
                        isSubmitting = false,
                        error = error.message
                    ) 
                }
            }
    }
}
```

---

## 📱 **SCREEN NAVIGATION MAP**

```
Landing
  ├─→ Login → AuthViewModel → Member Dashboard OR Platform Admin
  ├─→ Register → AuthViewModel → Group Creation OR Member Dashboard
  └─→ Browse Groups → GroupViewModel (List)

Group Profile (group/{groupId})
  ├─→ Join → Member Registration
  ├─→ View Members
  └─→ View Payments

Member Dashboard
  ├─→ Profile → Edit documents
  ├─→ Contributions → Payment history
  ├─→ Join Group → Browse Groups
  └─→ Settings → Logout

Admin Dashboard
  ├─→ Members Tab → Manage status
  ├─→ Payments Tab → View group payments
  ├─→ Settings Tab → Edit group fees
  ├─→ Metrics Tab → View actuarial data
  └─→ Logout

Platform Admin
  ├─→ Overview Tab → System metrics
  ├─→ Groups Tab → Manage all groups
  ├─→ Fee Management → Platform fees
  └─→ Analytics → Reports

Payment Screen
  └─→ YoCo Modal → Process payment → Back to dashboard
```

---

## 🔒 **SECURITY CHECKLIST**

- ✅ JWT stored in EncryptedSharedPreferences (not SharedPreferences)
- ✅ Tokens auto-refresh (alwaysAutoRefresh = true)
- ✅ No hardcoded credentials (all from local.properties → BuildConfig)
- ✅ RLS policies enforced in Supabase (users can only see own data)
- ✅ Service role key never sent to client (backend use only)
- ✅ No sensitive data logged (AppLogger.e() filters credentials)
- ✅ YoCo credentials injected via BuildConfig (not in code)

---

## 🐛 **TROUBLESHOOTING**

### "Connection Failed" on Splash Screen
```
1. Check local.properties for correct Supabase URL & key
2. Verify API keys in Supabase dashboard
3. Check internet connection on device
4. Try: Build → Clean → Rebuild
```

### "Enum not found" Error
```
1. Check if enum has @SerialName annotation matching DB value
2. Verify Room Converter uses safeValueOf() with fallback
3. Check JSON is configured with decodeEnumsCaseInsensitive = true
   (Should be in NetworkModule.kt)
```

### "Member not found" After Joining
```
1. Check Room cache was updated (toEntity mapper)
2. Verify Supabase insert succeeded (check dashboard)
3. Check RLS policies allow member to see own data
4. Try: Pull to refresh in member dashboard
```

### App Crashes on Payment
```
1. Check YoCo credentials in BuildConfig
2. Verify Payment model has @Serializable + @Parcelize
3. Check PaymentRepository.recordPayment() implementation
4. Check Room PaymentEntity schema matches
```

### Offline Data Not Showing
```
1. Verify observeWithCache() is used (not getWithCache)
2. Check Room database file exists (app/build/generated/...)
3. Try: Clear app data → Relaunch → Go online → Load data
4. Verify isLenient = true in JSON config
```

---

## 📞 **GETTING HELP**

### Before Asking for Help
1. Check the error logs (Android Studio Logcat)
2. Review the relevant test case in COMPLETE_TESTING_GUIDE.md
3. Check if model has @Serializable annotations
4. Check if repository is injected via @Inject (not new)
5. Check if ViewModel uses Flow (not LiveData)

### Common Gotchas
- ❌ Never use `.name.lowercase()` for enum serialization
- ❌ Never inject Context into ViewModel
- ❌ Never use LiveData (use Flow only)
- ❌ Never query repository directly from Composable
- ❌ Never hardcode credentials in code

---

## 📚 **DOCUMENTATION INDEX**

| Document | Purpose |
|----------|---------|
| AGENTS.md | Complete architecture guide |
| APP_TESTING_AND_FIXES.md | Fixes applied + verification |
| COMPLETE_TESTING_GUIDE.md | 40+ manual test cases |
| CLAUDE.md | Quick project rules |
| README.md | Project overview |
| DEVELOPERS.md | Onboarding guide |

---

## ✅ **PRE-RELEASE CHECKLIST**

- [ ] All enum serialization bugs fixed (4 instances corrected)
- [ ] Unit tests pass (40+ test cases)
- [ ] Integration tests pass
- [ ] Manual QA complete (all 11 phases)
- [ ] No crashes in Crashlytics
- [ ] No "TODO" comments in production code
- [ ] Signing keystore configured
- [ ] Release APK builds successfully
- [ ] Secrets in local.properties (not committed)
- [ ] RLS policies tested and working

---

**Status**: ✅ **READY FOR TESTING & QA**  
**Last Updated**: April 1, 2026  
**Next Step**: Run test suite from COMPLETE_TESTING_GUIDE.md

