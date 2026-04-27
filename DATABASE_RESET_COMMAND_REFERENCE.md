# Database Reset - Developer Command Reference

**Quick commands and syntax for database reset operations**

---

## 🚀 One-Liner Execution

### Full Reset (Recommended)
```bash
# Step 1: Run test
Right-click: resetDatabaseAndCreateAdmin() → Run

# Step 2: Clear cache
adb shell pm clear com.sanibonani.save

# Step 3: Restart
adb shell am start -n com.sanibonani.save/.MainActivity
```

---

## 📱 ADB Commands

### Clear App Cache
```bash
adb shell pm clear com.sanibonani.save
```

### Force Stop App
```bash
adb shell am force-stop com.sanibonani.save
```

### Start App
```bash
adb shell am start -n com.sanibonani.save/.MainActivity
```

### Clear App Data Completely
```bash
adb shell pm clear --cache com.sanibonani.save
```

### Uninstall & Reinstall
```bash
adb uninstall com.sanibonani.save
# Then reinstall from IDE
```

### Check Package Is Installed
```bash
adb shell pm list packages | grep sanibonani
```

### View App Info
```bash
adb shell dumpsys package com.sanibonani.save | grep -A5 "versionName"
```

---

## 🧪 Test Execution Methods

### Method 1: IDE Right-Click (Recommended)
```
File: DatabaseResetUtility.kt
Action: Right-click method → Run
```

### Method 2: Keyboard Shortcut
```
Windows: Ctrl+Shift+F10
Mac: Cmd+Shift+R
Position cursor on method name first
```

### Method 3: Run Menu
```
Gradle
  → Tasks
    → test
      → resetDatabaseAndCreateAdmin
```

### Method 4: Command Line
```bash
./gradlew test --tests "com.sanibonani.save.DatabaseResetUtility.resetDatabaseAndCreateAdmin"
```

---

## 🔍 Verification Commands

### Check Database Schema
```sql
-- In Supabase SQL Editor
SELECT 
  schemaname, 
  tablename 
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY tablename;
```

### Count Records in Each Table
```sql
SELECT 'groups' as table_name, COUNT(*) as count FROM groups
UNION ALL
SELECT 'members', COUNT(*) FROM members
UNION ALL
SELECT 'contributions', COUNT(*) FROM contributions
UNION ALL
SELECT 'payments', COUNT(*) FROM payments
UNION ALL
SELECT 'beneficiaries', COUNT(*) FROM beneficiaries
UNION ALL
SELECT 'notifications', COUNT(*) FROM notifications
UNION ALL
SELECT 'payouts', COUNT(*) FROM payouts
UNION ALL
SELECT 'member_documents', COUNT(*) FROM member_documents;
```

### Verify Admin User
```sql
SELECT id, email, user_metadata
FROM auth.users
WHERE email = 'torryymsimango@gmail.com';
```

### List All Auth Users
```sql
SELECT id, email, email_confirmed_at, user_metadata
FROM auth.users
ORDER BY created_at DESC;
```

---

## 🔧 Gradle Commands

### Build Project
```bash
./gradlew build
```

### Clean Build
```bash
./gradlew clean build
```

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test
```bash
./gradlew test --tests "DatabaseResetUtility"
```

### Build & Install on Device
```bash
./gradlew installDebug
```

### Debug Build
```bash
./gradlew assembleDebug
```

---

## 📊 Logcat Commands

### Show All Logs
```bash
adb logcat
```

### Filter by Package
```bash
adb logcat | grep -i sanibonani
```

### Clear Logs
```bash
adb logcat -c
```

### Show Errors Only
```bash
adb logcat *:E
```

### Show Warnings and Errors
```bash
adb logcat *:W
```

### Save Logs to File
```bash
adb logcat > logcat.txt
```

### Live Filter (Most Useful)
```bash
adb logcat com.sanibonani.save:V *:S
```

---

## 🗄️ Database Inspection

### Local Room Database Path
```bash
adb shell ls -la /data/data/com.sanibonani.save/databases/
```

### Delete Local Database
```bash
adb shell rm /data/data/com.sanibonani.save/databases/sanibonani.db*
```

### Backup Local Database
```bash
adb pull /data/data/com.sanibonani.save/databases/ ./db_backup/
```

### Restore Local Database
```bash
adb push ./db_backup/ /data/data/com.sanibonani.save/databases/
```

---

## 🔑 Authentication

### Get Service Role Key from local.properties
```bash
grep "SUPABASE_SERVICE_ROLE_KEY" local.properties
```

### Get Anon Key
```bash
grep "SUPABASE_ANON_KEY" local.properties
```

### Get URL
```bash
grep "SUPABASE_URL" local.properties
```

---

## 📋 Common Test Patterns

### Run Just the Reset
```kotlin
@Test
fun resetDatabaseAndCreateAdmin() = runBlocking { /* ... */ }
```

### Run Just Clear Data
```kotlin
@Test
fun clearRemoteDataOnly() = runBlocking { /* ... */ }
```

### Run Just Create Admin
```kotlin
@Test
fun createAdminUserOnly() = runBlocking { /* ... */ }
```

---

## 🐛 Debugging

### Enable Verbose Logging
```bash
adb shell setprop log.tag.com.sanibonani.save VERBOSE
```

### View Shared Preferences
```bash
adb shell cat /data/data/com.sanibonani.save/shared_prefs/*.xml
```

### Get App Crash Stack Trace
```bash
adb logcat | grep -A20 "AndroidRuntime"
```

### Check Memory Usage
```bash
adb shell dumpsys meminfo com.sanibonani.save
```

---

## 💾 Backup & Restore

### Full App Backup
```bash
adb backup -apk com.sanibonani.save -f backup.ab
```

### Restore App
```bash
adb restore backup.ab
```

### Backup Shared Preferences Only
```bash
adb pull /data/data/com.sanibonani.save/shared_prefs/ ./prefs_backup/
```

---

## 🔄 Common Workflows

### Complete Reset Workflow
```bash
# Step 1: Run test
# (See IDE instructions above)

# Step 2: Wait for completion
# Watch for: "DATABASE RESET COMPLETE" in console

# Step 3: Clear cache
adb shell pm clear com.sanibonani.save

# Step 4: Restart app
adb shell am start -n com.sanibonani.save/.MainActivity

# Step 5: Verify in UI
# Sign in with: torryymsimango@gmail.com / torry123M
```

### Debug Crash Workflow
```bash
# Step 1: Clear logcat
adb logcat -c

# Step 2: Run app
adb shell am start -n com.sanibonani.save/.MainActivity

# Step 3: Trigger crash
# (Perform action that crashes)

# Step 4: Capture logs
adb logcat > crash.log

# Step 5: Examine
grep -i "exception\|error\|fatal" crash.log
```

### Verify Reset Worked
```bash
# Step 1: Check Supabase (use SQL above)
# Step 2: Check app data:
adb shell ls /data/data/com.sanibonani.save/databases/

# Step 3: Check auth:
# (Check in Supabase dashboard)

# Step 4: Sign in:
# Email: torryymsimango@gmail.com
# Password: torry123M
```

---

## 📚 Quick Reference Table

| Task | Command |
|------|---------|
| Run reset test | Right-click method → Run |
| Clear app cache | `adb shell pm clear com.sanibonani.save` |
| Restart app | `adb shell am start -n com.sanibonani.save/.MainActivity` |
| View logs | `adb logcat` |
| Check tables | See SQL section |
| Verify admin | See SQL section |
| Delete database | `adb shell rm /data/data/com.sanibonani.save/databases/*` |
| Force stop | `adb shell am force-stop com.sanibonani.save` |
| Run Gradle build | `./gradlew build` |

---

## 🎯 Fastest Reset Sequence

```bash
# Terminal 1: Run test (IDE)
# Right-click: resetDatabaseAndCreateAdmin() → Run
# Wait for console: "DATABASE RESET COMPLETE"

# Terminal 2: Clear cache & restart
adb shell pm clear com.sanibonani.save && \
adb shell am start -n com.sanibonani.save/.MainActivity
```

---

## 🔗 References

- **Full Documentation:** See `DATABASE_RESET_ADMIN_CREATION_GUIDE.md`
- **Quick Guide:** See `QUICK_DATABASE_RESET.md`
- **Execution Checklist:** See `DATABASE_RESET_EXECUTION_CHECKLIST.md`
- **Visual Guide:** See `DATABASE_RESET_VISUAL_GUIDE.md`

---

## ⚡ Pro Tips

1. **Save these commands** in a shell script for automation
2. **Use aliases** for frequently used commands
3. **Redirect logcat** to file for analysis
4. **Create backup** before running reset
5. **Test on emulator** first, then device

---

## 🚨 Emergency Commands

### If App Won't Start
```bash
adb shell am clear-debug-app com.sanibonani.save
adb shell am start -n com.sanibonani.save/.MainActivity
```

### If Data Corrupted
```bash
adb shell rm /data/data/com.sanibonani.save/shared_prefs/*.xml
adb shell am start -n com.sanibonani.save/.MainActivity
```

### If Cache Issues
```bash
adb shell pm clear com.sanibonani.save
adb shell pm trim-caches 512M
```

---

**Version:** 1.0  
**Last Updated:** April 16, 2026  
**Status:** ✅ Ready to Use

