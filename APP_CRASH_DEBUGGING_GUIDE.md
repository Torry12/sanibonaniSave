# App Crash Debugging & Solutions — SanibonaniSave

**Updated**: April 1, 2026  
**Status**: Additional fixes applied for common startup crash causes

---

## ✅ Fixes Applied (This Session)

### 1. **DI Configuration** (Previous Fix)
- ✅ Moved `provideJson()` to AppModule
- ✅ Removed duplicate from NetworkModule

### 2. **BuildConfig Property Handling** (NEW)
- ✅ Enhanced `getSafeProp()` function to properly handle quotes
- ✅ Added better empty value detection

### 3. **Supabase Initialization Validation** (NEW)
- ✅ Improved error messages for configuration issues
- ✅ Better null/empty checking

### 4. **Database Migration Chain** (NEW)
- ✅ Added missing MIGRATION_6_7 and MIGRATION_7_8
- ✅ Made migrations 8_9 more robust (try-catch for existing columns)

### 5. **Database Creation Resilience** (NEW)
- ✅ Added fallback logic if database creation fails
- ✅ Automatically recreates database if schema issues occur

---

## 🔍 How to Identify the Exact Crash

### Step 1: Check Logcat for Error Messages

Look for these specific patterns:

```
# DI/Hilt Errors
"MissingBindingException"
"DependencyResolutionException"
"Hilt"

# BuildConfig Errors
"SUPABASE_URL not properly configured"
"SUPABASE_ANON_KEY not properly configured"

# Database Errors
"Room"
"SQLiteDatabase"
"schema validation"

# Supabase Connection Errors
"SupabaseException"
"IOException"
"NetworkException"

# General Crash
"FATAL EXCEPTION"
"AndroidRuntime"
"NullPointerException"
```

### Step 2: Filter Logcat

```bash
# View all errors
adb logcat E/*:S | grep -E "(E/|crash|Exception)"

# View Hilt errors only
adb logcat | grep -i hilt

# View full stack trace
adb logcat AndroidRuntime:E *:S
```

---

## 🛠️ Common Crash Causes & Solutions

### Cause #1: BuildConfig Values Empty or Wrong

**Symptoms:**
- Crash immediately on launch
- LogCat shows: "SUPABASE_URL not properly configured"

**Solution:**
```bash
# 1. Verify local.properties has values:
cat local.properties | grep SUPABASE

# 2. Expected output (example):
# SUPABASE_URL=https://prosbbknupoexgzjwrwr.supabase.co
# SUPABASE_ANON_KEY=eyJhbGc...

# 3. If empty, add real values
# 4. Then rebuild:
./gradlew clean build
```

### Cause #2: Database Schema Mismatch

**Symptoms:**
- Crash after splash screen
- LogCat shows: "schema validation failed" or "SQLiteException"

**Solution:**
```bash
# 1. Clear app data:
adb shell pm clear com.sanibonani.save

# 2. Uninstall and reinstall:
adb uninstall com.sanibonani.save
./gradlew installDebug

# 3. Or manually delete database:
adb shell rm /data/data/com.sanibonani.save/databases/sanibonani.db*
```

### Cause #3: Hilt DI Resolution Failed

**Symptoms:**
- Immediate crash
- LogCat shows: "MissingBindingException" or "DependencyResolutionException"

**Solution:**
```bash
# 1. Clean and rebuild Hilt annotations:
./gradlew clean build --no-build-cache

# 2. Check for circular dependencies in AppModule.kt and NetworkModule.kt

# 3. Ensure all @Inject ViewModels have proper @HiltViewModel annotation

# 4. Verify all repository implementations have @Inject constructor
```

### Cause #4: Supabase Connection Timeout

**Symptoms:**
- Splash screen hangs indefinitely
- LogCat shows network timeouts
- Eventually crashes after 10 seconds

**Solution:**
```bash
# 1. Check internet connection:
adb shell ping google.com

# 2. Check Supabase credentials are valid:
# Visit https://app.supabase.com and verify:
# - Project URL matches local.properties SUPABASE_URL
# - Anon key matches local.properties SUPABASE_ANON_KEY

# 3. Check proxy/firewall isn't blocking Supabase API
```

### Cause #5: Room Database Corruption

**Symptoms:**
- Crash occurs after working previously
- LogCat shows: "database corruption" or "general error"

**Solution:**
```bash
# 1. Delete the corrupted database:
adb shell rm /data/data/com.sanibonani.save/databases/sanibonani.db*

# 2. Reinstall app:
./gradlew installDebug

# 3. Or via UI: Settings → Apps → SanibonaniSave → Storage → Clear Data
```

---

## 🔧 Step-by-Step Debugging Process

### Step 1: Identify the Crash Point

```kotlin
// Add logging at app startup to see where it crashes:

// In SanibonaniApp.kt:
@HiltAndroidApp
class SanibonaniApp : Application() {
    override fun onCreate() {
        Log.d("SanibonaniApp", "onCreate() called")
        super.onCreate()
        Log.d("SanibonaniApp", "Hilt initialized")
        setupOsmDroid()
        Log.d("SanibonaniApp", "OSM setup done")
        setupNotificationChannels()
        Log.d("SanibonaniApp", "Notifications setup done")
    }
}

// In MainActivity.kt SplashViewModel:
init {
    Log.d("SplashViewModel", "ViewModel initialized")
    checkConnection()
}

fun checkConnection() {
    Log.d("SplashViewModel", "checkConnection() called")
    viewModelScope.launch {
        Log.d("SplashViewModel", "Attempting Supabase connection...")
        // ... rest of function
    }
}
```

### Step 2: Verify Each Module

1. **Check if Hilt initializes** → Look for "Hilt initialized" in logcat
2. **Check if MainActivity starts** → Look for onCreate() logs
3. **Check if SplashViewModel creates** → Look for "ViewModel initialized"
4. **Check if Supabase connects** → Look for "Attempting Supabase connection"

### Step 3: Isolate the Problem

Once you know WHERE it crashes, check:

| Location | Probable Cause |
|----------|-----------------|
| Before "Hilt initialized" | DI config error / AppModule issue |
| Before MainActivity.onCreate | Manifest or theme issue |
| Before SplashViewModel | ViewModel injection issue |
| During Supabase connection | BuildConfig / network issue |

---

## 📋 Full Verification Checklist

Run through these checks before reporting a crash:

### ✅ Build Configuration
- [ ] `./gradlew clean build` completes without errors
- [ ] No "Hilt" warnings in build output
- [ ] No "unresolved reference" errors

### ✅ Local Configuration
- [ ] `local.properties` exists
- [ ] `SUPABASE_URL` is set to real URL (not placeholder)
- [ ] `SUPABASE_ANON_KEY` is set (not placeholder)
- [ ] No extra quotes in properties file

### ✅ App Installation
- [ ] `adb uninstall com.sanibonani.save` (if installed)
- [ ] `./gradlew installDebug` completes
- [ ] App appears on device

### ✅ Crash Analysis
- [ ] App launches (splash screen appears)
- [ ] Logcat shows NO errors in first 5 seconds
- [ ] Splash screen shows "Connecting to community wealth..."
- [ ] After 1-2 seconds, either:
  - ✅ Navigation screen appears (connection successful), OR
  - ✅ "Connection Failed" error screen appears (credentials OK, network issue)

---

## 🆘 If Still Crashing

### Gather Diagnostic Information

```bash
# 1. Get full logcat output
adb logcat > crash_log.txt
# Then trigger crash and let it run 30 seconds

# 2. Get BuildConfig values being used
adb shell run-as com.sanibonani.save cat /data/data/com.sanibonani.save/files/

# 3. Get database schema
adb shell run-as com.sanibonani.save sqlite3 /data/data/com.sanibonani.save/databases/sanibonani.db ".schema"

# 4. Check device info
adb shell getprop ro.build.version.sdk  # Android version
adb shell getprop ro.product.model      # Device model
```

### Export Diagnostic Bundle

Create a file with all diagnostic info:

```bash
#!/bin/bash
echo "=== Crash Diagnostics ===" > diagnostics.txt
echo "Date: $(date)" >> diagnostics.txt
echo "" >> diagnostics.txt

echo "=== Device Info ===" >> diagnostics.txt
adb shell getprop ro.build.version.sdk >> diagnostics.txt
adb shell getprop ro.product.model >> diagnostics.txt
echo "" >> diagnostics.txt

echo "=== Build Config ===" >> diagnostics.txt
adb shell dumpsys package com.sanibonani.save | grep versionName >> diagnostics.txt
echo "" >> diagnostics.txt

echo "=== Recent Logcat ===" >> diagnostics.txt
adb logcat -d >> diagnostics.txt
```

---

## 📚 Reference Documents

See these files for more detail:

- `CRASH_FIX_SUMMARY.md` — DI fix explanation
- `CRASH_FIX_VISUAL_DIAGRAM.md` — DI architecture diagrams
- `AGENTS.md` § "DI via Hilt" — Architecture guidelines
- `CLAUDE.md` — Project coding rules

---

## 🚀 Quick Recovery Steps

If app is crashing and you need it to work NOW:

```bash
# 1. Clean everything
./gradlew clean

# 2. Uninstall previous version
adb uninstall com.sanibonani.save

# 3. Delete local build cache
rm -rf .gradle/

# 4. Rebuild from scratch
./gradlew assembleDebug

# 5. Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 6. Run and check logcat
adb logcat -c  # Clear
adb shell am start -n com.sanibonani.save/.MainActivity
adb logcat | head -50
```

---

**Next**: If still crashing after these steps, run diagnostics and compare against known error patterns above.


