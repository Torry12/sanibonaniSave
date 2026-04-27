# ⚡ Quick Crash Fix Reference Card

## 🔴 App Is Crashing?

### Immediate Actions (Do These First)

```bash
# 1. CLEAN BUILD
./gradlew clean build

# 2. UNINSTALL OLD VERSION
adb uninstall com.sanibonani.save

# 3. FRESH INSTALL
./gradlew installDebug

# 4. WATCH LOGCAT
adb logcat | grep -E "(E/|Hilt|crash|Exception)" | head -20
```

**Expected**: App launches, splash screen appears, then connects to Supabase

---

## 🟡 If Still Crashing

### Check What's In LogCat

**Hilt Error?**
```
MissingBindingException / DependencyResolutionException
→ See: CRASH_FIX_VISUAL_DIAGRAM.md
```

**Config Error?**
```
SUPABASE_URL / SUPABASE_ANON_KEY not properly configured
→ Check: local.properties has real values
```

**Database Error?**
```
SQLite / schema validation
→ Run: adb shell rm /data/data/com.sanibonani.save/databases/sanibonani.db*
```

**Network Error?**
```
IOException / ConnectionException
→ Check: Internet connection, Supabase credentials
```

---

## ✅ Fixes That Were Applied

| What | Where | Status |
|------|-------|--------|
| DI Config | `AppModule.kt` | ✅ Fixed |
| BuildConfig | `build.gradle.kts` | ✅ Enhanced |
| Supabase Validation | `AppModule.kt` | ✅ Improved |
| DB Migrations | `Migrations.kt` | ✅ Complete |
| DB Recovery | `AppModule.kt` | ✅ Added |

---

## 📋 Local.properties Checklist

```ini
# ✅ CORRECT (should look like this):
SUPABASE_URL=https://prosbbknupoexgzjwrwr.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiI...

# ❌ WRONG (don't do this):
SUPABASE_URL=https://your-project.supabase.co  # Placeholder!
SUPABASE_ANON_KEY=your-anon-key-here           # Placeholder!
```

---

## 🔍 Step-by-Step Debugging

```kotlin
// 1. Add logging to find where crash happens
Log.d("DEBUG", "Step 1: App starting")        // App.onCreate()
Log.d("DEBUG", "Step 2: Hilt init")           // @HiltAndroidApp
Log.d("DEBUG", "Step 3: MainActivity")        // MainActivity.onCreate()
Log.d("DEBUG", "Step 4: ViewModel created")   // SplashViewModel.init
Log.d("DEBUG", "Step 5: Connecting...")       // checkConnection()
```

**Read logcat** → Find which step fails

---

## 🚀 Nuclear Option (Last Resort)

```bash
# Complete clean slate
./gradlew clean
rm -rf .gradle/ app/build/
adb shell pm clear com.sanibonani.save
adb uninstall com.sanibonani.save

# Rebuild
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📞 Still Crashing?

1. Check: `APP_CRASH_DEBUGGING_GUIDE.md` (comprehensive guide)
2. Check: `CRASH_FIX_VISUAL_DIAGRAM.md` (architecture)
3. Gather: Logcat output (see debugging guide)
4. Compare: Your error against known patterns

---

**Last Updated**: April 1, 2026  
**All Fixes Applied**: ✅ Yes  
**Ready to Test**: ✅ Yes


