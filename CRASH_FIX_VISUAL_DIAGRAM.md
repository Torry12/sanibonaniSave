# Dependency Injection Fix — Visual Diagram

## BEFORE (❌ Broken — App Crashing)

```
┌─────────────────────────────────────────────────────────────────┐
│                      HILT DI GRAPH (BEFORE)                    │
└─────────────────────────────────────────────────────────────────┘

AppModule
├── provideSupabaseClient(json: Json) ← WHERE IS JSON?
│   ├── Depends on: Json (MISSING or CONFLICTING)
│   └── ❌ CRASH: Can't find Json provider!
├── provideDatabase(context)
└── provideGroupRepository(...)

NetworkModule
├── provideJson() ← Provides Json
├── provideRetrofit(json: Json)
├── provideOkHttpClient()
└── provideWhatsAppApiService(json: Json)

❌ PROBLEM: 
   - If NetworkModule hasn't initialized yet → Json not available
   - If both modules try to provide Json → Hilt confused (duplicate)
   - Either way → CRASH!
```

---

## AFTER (✅ Fixed — App Launches)

```
┌─────────────────────────────────────────────────────────────────┐
│                      HILT DI GRAPH (AFTER)                     │
└─────────────────────────────────────────────────────────────────┘

AppModule (PRIMARY DI MODULE)
├── provideJson() ✅ SINGLE SOURCE OF TRUTH
│   └── Json { ignoreUnknownKeys = true, ... }
│
├── provideSupabaseClient(json: Json) ✅ GETS JSON FROM APPMODULE
│   ├── Depends on: AppModule.provideJson()
│   └── SupabaseClient instance created ✅
│
├── provideSupabaseManager(supabase)
│   └── SupabaseManager instance ✅
│
├── provideDatabase(context)
│   └── SanibonaniDatabase instance ✅
│
├── provideGroupRepository(supabase, db, ...)
├── provideMemberRepository(supabase, db, ...)
├── provideNotificationRepository(supabase, whatsApp)
├── providePaymentRepository(supabase, db)
├── provideActuarialRepository(group, member)
└── provideInvestmentRepository()
    └── All 6 repositories instantiated ✅


NetworkModule (SECONDARY DI MODULE — USES APPMODULE'S JSON)
├── provideOkHttpClient()
│   └── OkHttpClient instance ✅
│
├── provideRetrofit(okHttpClient, json) ✅ USES APPMODULE.JSON
│   └── Retrofit instance ✅
│
├── provideGeoapifyService(okHttpClient, json) ✅ USES APPMODULE.JSON
│   └── GeoapifyService instance ✅
│
├── providePolicyApiService(retrofit)
│   └── PolicyApiService instance ✅
│
└── provideWhatsAppApiService(okHttpClient, json) ✅ USES APPMODULE.JSON
    └── WhatsAppApiService instance ✅


✅ RESULT:
   - Json provided ONCE in AppModule
   - All modules depend on it (clear chain)
   - No conflicts, no missing dependencies
   - App launches successfully!
```

---

## Dependency Chain Visualization

### Before (Circular/Conflicting):
```
SplashViewModel
    ↓ @Inject
SupabaseManager
    ↓ @Inject
SupabaseClient
    ↓ ?????
Json (WHERE?)
    ├── AppModule.provideJson()? (NOT INITIALIZED YET?)
    └── NetworkModule.provideJson()? (DUPLICATE?)
        
❌ CRASH: Hilt can't resolve Json dependency
```

### After (Clear Linear Chain):
```
SplashViewModel
    ↓ @Inject
SupabaseManager
    ↓ @Inject
SupabaseClient
    ↓ @Inject
Json (from AppModule.provideJson())
    ✅ FOUND: AppModule provides it (single source of truth)
    
MainActivity
    ↓
SanibonaniApp (Hilt initialized)
    ↓ Initializes all @Module @Singleton providers
    ├── AppModule.provideJson()
    ├── AppModule.provideSupabaseClient(json) ✅ HAS JSON
    ├── AppModule.provideDatabase(context)
    ├── AppModule.all repositories
    ├── NetworkModule.provideOkHttpClient()
    ├── NetworkModule.provideRetrofit(json) ✅ HAS JSON
    ├── NetworkModule.provideGeoapifyService(json) ✅ HAS JSON
    └── NetworkModule.provideWhatsAppApiService(json) ✅ HAS JSON
    
✅ ALL DEPENDENCIES RESOLVED → APP LAUNCHES!
```

---

## File Changes Summary

```
┌────────────────────────────────────────────────────────────────┐
│ AppModule.kt (BEFORE)                                          │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│ @Module                                                        │
│ object AppModule {                                             │
│                                                                │
│     @Provides @Singleton                                       │
│     fun provideSupabaseClient(json: Json): SupabaseClient {   │
│         // ❌ Where does 'json' come from?                    │
│     }                                                          │
│ }                                                              │
└────────────────────────────────────────────────────────────────┘

                              ↓↓↓ FIX ↓↓↓

┌────────────────────────────────────────────────────────────────┐
│ AppModule.kt (AFTER)                                           │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│ @Module                                                        │
│ object AppModule {                                             │
│                                                                │
│     @Provides @Singleton                                       │
│     fun provideJson(): Json = Json { ... }  ← NEW!            │
│                                                                │
│     @Provides @Singleton                                       │
│     fun provideSupabaseClient(json: Json): SupabaseClient {   │
│         // ✅ 'json' comes from provideJson()!               │
│     }                                                          │
│ }                                                              │
└────────────────────────────────────────────────────────────────┘


┌────────────────────────────────────────────────────────────────┐
│ NetworkModule.kt (BEFORE)                                      │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│ @Module                                                        │
│ object NetworkModule {                                         │
│                                                                │
│     @Provides @Singleton                                       │
│     fun provideJson(): Json = Json { ... }  ← DUPLICATE!      │
│                                                                │
│     @Provides @Singleton                                       │
│     fun provideWhatsAppApiService(json: Json) { ... }         │
│ }                                                              │
└────────────────────────────────────────────────────────────────┘

                              ↓↓↓ FIX ↓↓↓

┌────────────────────────────────────────────────────────────────┐
│ NetworkModule.kt (AFTER)                                       │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│ @Module                                                        │
│ object NetworkModule {                                         │
│                                                                │
│     // ✅ REMOVED: provideJson() (no longer duplicate)        │
│                                                                │
│     @Provides @Singleton                                       │
│     fun provideWhatsAppApiService(json: Json) { ... }         │
│     // ✅ Now depends on AppModule.provideJson()              │
│ }                                                              │
└────────────────────────────────────────────────────────────────┘
```

---

## Why Hilt Failed (Technical Explanation)

Hilt uses **component dependency injection** with these rules:

1. **One provider per type per component**
   - ❌ BEFORE: Json provided by BOTH AppModule and NetworkModule
   - ✅ AFTER: Json provided by ONLY AppModule

2. **Deterministic initialization order**
   - ❌ BEFORE: Can't guarantee NetworkModule initializes before AppModule.provideSupabaseClient()
   - ✅ AFTER: AppModule initializes first, provides Json, then all modules use it

3. **No circular dependencies**
   - ❌ BEFORE: AppModule → ?Json? → NetworkModule (circular ambiguity)
   - ✅ AFTER: AppModule → NetworkModule (clear hierarchy)

---

## Result: App Flow

```
App Start
    ↓
SanibonaniApp (Hilt @HiltAndroidApp)
    ↓ Hilt scans all @Module classes
    ↓ 
    Initializes AppModule
    ├── provideJson() → Creates singleton Json instance
    ├── provideSupabaseClient(json) → Creates singleton SupabaseClient ✅ HAS JSON
    ├── provideSupabaseManager(supabase) → Creates SupabaseManager
    ├── provideDatabase(context) → Creates Room database
    └── ... all repositories
    ↓
    Initializes NetworkModule  
    ├── provideOkHttpClient() → Creates OkHttpClient
    ├── provideRetrofit(okHttpClient, json) → Uses AppModule.json ✅
    └── ... all network services
    ↓
MainActivity.onCreate()
    ↓
viewModel: SplashViewModel @Inject constructor(
    supabaseManager: SupabaseManager ← ✅ Resolved from AppModule
)
    ↓
SplashViewModel.checkConnection()
    ↓
App launches successfully! ✅
```

---

## Summary

| Aspect | Before | After |
|--------|--------|-------|
| Json Providers | 2 (duplicate) | 1 (single) |
| Provider Location | Both AppModule & NetworkModule | AppModule only |
| Dependency Chain | Ambiguous/Circular | Clear & Linear |
| App Result | ❌ CRASH | ✅ LAUNCHES |


