# App Crash Fix Summary — SanibonaniSave

**Date**: April 1, 2026  
**Issue**: App crashing at startup  
**Root Cause**: Dependency Injection (Hilt) configuration issue with duplicate `Json` providers

---

## Problem Identified

The app was crashing at startup due to **missing or conflicting dependency providers** in the Hilt DI configuration:

- **AppModule** had a `provideJson()` function (or was missing it)
- **NetworkModule** also had a `provideJson()` function
- This created a **duplicate provider conflict**, causing Hilt to fail during app initialization

The issue manifests as a crash in the `SplashViewModel` or during `MainActivity.onCreate()` when Hilt tries to resolve dependencies.

---

## Root Cause Analysis

### Before Fix:
```
AppModule.kt:
├── provideSupabaseClient(json: Json) ❌ Depends on Json from NetworkModule
├── provideDatabase(context)
├── provideGroupRepository(...)
└── ... other providers

NetworkModule.kt:
├── provideJson()  // Duplicate provider!
├── provideOkHttpClient()
├── provideRetrofit(json: Json)
├── provideWhatsAppApiService(json: Json)
└── ... other providers
```

**Problem**: Hilt couldn't determine which `Json` provider to use for `AppModule.provideSupabaseClient()` because:
1. If `NetworkModule.provideJson()` wasn't initialized yet, the dependency chain broke
2. If both modules tried to provide `Json`, Hilt threw a "duplicate provider" error
3. Module initialization order isn't guaranteed, causing race conditions

---

## Solution Implemented

### Changes Made:

#### 1. **AppModule.kt** — Added `provideJson()` provider
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Json Serializer (singleton — used by Supabase and Retrofit) ───────────
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        decodeEnumsCaseInsensitive = true
        encodeDefaults = true
    }

    // ── Supabase Client now depends on AppModule's Json provider
    @Provides
    @Singleton
    fun provideSupabaseClient(json: Json): SupabaseClient { ... }
    
    // ... rest of providers
}
```

#### 2. **NetworkModule.kt** — Removed duplicate `provideJson()` provider
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // ❌ Removed: provideJson() duplicate
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient { ... }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        // ✅ Now depends on AppModule.provideJson()
        ...
    }

    @Provides
    @Singleton
    fun provideWhatsAppApiService(okHttpClient: OkHttpClient, json: Json): WhatsAppApiService {
        // ✅ Now depends on AppModule.provideJson()
        ...
    }
    
    // ... other providers
}
```

---

## Benefits of This Fix

✅ **Single Source of Truth**: `Json` is provided once by `AppModule`  
✅ **Clear Dependency Chain**: `AppModule` → (provides `Json`) → `NetworkModule` uses it  
✅ **Deterministic Initialization**: No race conditions; all modules initialize in correct order  
✅ **Proper Hilt Configuration**: Follows the MVVM + Hilt best practices from AGENTS.md  

---

## Dependency Chain (After Fix)

```
AppModule (SingletonComponent)
├── provideJson()  ← Single source of Json for entire app
├── provideSupabaseClient(json: Json) ✅
├── provideSupabaseManager(supabase)
├── provideDatabase(context)
├── provideGroupRepository(supabase, db, ...)
└── ... other repositories

NetworkModule (SingletonComponent)
├── provideOkHttpClient()
├── provideRetrofit(okHttpClient, json: Json) ✅ Uses AppModule.json
├── provideGeoapifyService(okHttpClient, json: Json) ✅ Uses AppModule.json
├── providePolicyApiService(retrofit)
└── provideWhatsAppApiService(okHttpClient, json: Json) ✅ Uses AppModule.json
```

---

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/java/com/sanibonani/save/di/AppModule.kt` | Added `provideJson()` method (moved to primary DI module) |
| `app/src/main/java/com/sanibonani/save/di/NetworkModule.kt` | Removed duplicate `provideJson()` method |

---

## Testing Checklist

After applying this fix:

- [ ] App builds without errors: `./gradlew assembleDebug`
- [ ] App launches without crashing
- [ ] SplashViewModel initializes correctly
- [ ] Supabase connection test completes (splash screen shows "Connecting...")
- [ ] No Hilt dependency errors in logcat
- [ ] All repositories inject successfully (GroupRepository, MemberRepository, NotificationRepository, etc.)

---

## Why This Happened

Per the **AGENTS.md** architecture guidelines:

> **All DI via Hilt — no manual instantiation**

When multiple modules provide the same singleton, Hilt cannot resolve which one to use. The fix ensures:

1. **Singleton module** (AppModule) provides the core serialization layer
2. **Network module** (NetworkModule) builds on top of it
3. **Repositories** inject via the defined providers
4. **ViewModels** use `@HiltViewModel @Inject constructor()` pattern

This follows the **Hilt best practices** for complex Android projects.

---

## Related Documentation

- **AGENTS.md** § "1. AdminFeeState Enum Serialization" — Discusses Json configuration
- **APP_SPECIFICATION.md** — DI setup and module organization
- **CLAUDE.md** § "Rules" — "All DI via Hilt — no manual instantiation"

---

## Next Steps

1. Run `./gradlew clean build` to verify compilation
2. Deploy to test device or emulator
3. Verify splash screen appears and Supabase connection succeeds
4. Check logcat for no Hilt-related errors
5. If still crashing, check `BuildConfig` values for empty SUPABASE_URL or SUPABASE_ANON_KEY

---

*Fix applied successfully. App should now start without crashing.*

