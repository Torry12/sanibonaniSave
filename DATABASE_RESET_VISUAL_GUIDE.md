# Database Reset - Visual Execution Guide

## 🎬 Step-by-Step Visual Guide

### Phase 1: Preparation (2 minutes)

```
┌─────────────────────────────────────────────────────┐
│  PREPARATION PHASE                                  │
├─────────────────────────────────────────────────────┤
│                                                       │
│  ✓ Step 1: Open Android Studio                      │
│    └─ Project loads                                 │
│                                                       │
│  ✓ Step 2: Navigate to test file                    │
│    └─ app/src/test/java/com/sanibonani/save/       │
│       DatabaseResetUtility.kt                       │
│                                                       │
│  ✓ Step 3: Find method                              │
│    └─ Look for: resetDatabaseAndCreateAdmin()       │
│                                                       │
│  ✓ Step 4: Ready to execute                         │
│    └─ File is open in editor                        │
│                                                       │
└─────────────────────────────────────────────────────┘

⏱️  Time: 2 minutes
```

---

### Phase 2: Execution (1-2 minutes)

```
┌─────────────────────────────────────────────────────┐
│  EXECUTION PHASE                                    │
├─────────────────────────────────────────────────────┤
│                                                       │
│  📌 Step 1: Right-click on method                   │
│                                                       │
│     resetDatabaseAndCreateAdmin()                   │
│     ↓ right-click                                   │
│     [Context Menu Appears]                          │
│                                                       │
│  📌 Step 2: Select "Run" from menu                  │
│                                                       │
│     ► Run 'resetDatabaseAndCreateAdmin'             │
│       ├─ Kotlin REPL Console                        │
│       └─ Run Tests                                  │
│                                                       │
│  📌 Step 3: Test execution starts                   │
│                                                       │
│     Gradle build begins...                          │
│     Dependencies resolving...                       │
│     Test runner initializing...                     │
│                                                       │
│  📌 Step 4: Watch console output                    │
│                                                       │
│     (See expected output below ↓)                   │
│                                                       │
│  📌 Step 5: Wait for completion                     │
│                                                       │
│     ✓ DATABASE RESET COMPLETE                       │
│                                                       │
└─────────────────────────────────────────────────────┘

⏱️  Time: 1-2 minutes
```

---

### Phase 3: Expected Console Output

```
════════════════════════════════════════════════════════════════════════════════
SANIBONANI SAVE - DATABASE RESET & ADMIN CREATION UTILITY
════════════════════════════════════════════════════════════════════════════════

[1/4] Initializing Supabase clients...
✓ Supabase clients initialized

[2/4] Clearing remote database tables...
  ✓ Cleared table: payouts
  ✓ Cleared table: member_documents
  ✓ Cleared table: beneficiaries
  ✓ Cleared table: notifications
  ✓ Cleared table: contributions
  ✓ Cleared table: payments
  ✓ Cleared table: members
  ✓ Cleared table: groups
✓ All data cleared from remote database

[3/4] Checking for existing admin user...
  ✓ Deleted existing admin user

[4/4] Creating Platform Admin user...
✓ Platform Admin user created successfully!
   Email: torryymsimango@gmail.com
   Role: platform_admin
   Password: torry123M
   User ID: 550e8400-e29b-41d4-a716-446655440000

════════════════════════════════════════════════════════════════════════════════
✓ DATABASE RESET COMPLETE
════════════════════════════════════════════════════════════════════════════════

Admin Login Credentials:
  Email: torryymsimango@gmail.com
  Password: torry123M

Next Steps:
  1. Clear local app cache: Settings > Apps > SanibonaniSave > Clear Cache
  2. Close and restart the app
  3. Sign in with the admin credentials above
════════════════════════════════════════════════════════════════════════════════

Process finished with exit code 0  ✓ SUCCESS
```

---

### Phase 4: Local Cache Cleanup (30 seconds)

```
┌─────────────────────────────────────────────────────┐
│  LOCAL CACHE CLEANUP                                │
├─────────────────────────────────────────────────────┤
│                                                       │
│  Option A: Using ADB (Recommended)                  │
│  ─────────────────────────────────────              │
│                                                       │
│  $ adb shell pm clear com.sanibonani.save           │
│                                                       │
│  ✓ Output: Success                                  │
│                                                       │
│  ─ OR ─                                             │
│                                                       │
│  Option B: Using Device Settings                    │
│  ──────────────────────────────────                 │
│                                                       │
│  Settings                                           │
│    └─ Apps                                          │
│       └─ SanibonaniSave                             │
│          └─ Storage & cache                         │
│             └─ Clear Cache                          │
│                └─ ✓ Confirmed                       │
│                                                       │
└─────────────────────────────────────────────────────┘

⏱️  Time: 30 seconds
```

---

### Phase 5: App Restart (1 minute)

```
┌─────────────────────────────────────────────────────┐
│  APP RESTART                                        │
├─────────────────────────────────────────────────────┤
│                                                       │
│  ✓ Step 1: Close SanibonaniSave completely         │
│    └─ Force stop if needed                          │
│    └─ Wait 2 seconds                                │
│                                                       │
│  ✓ Step 2: Re-open SanibonaniSave                   │
│    └─ Tap app icon                                  │
│    └─ Initialization happens                        │
│    └─ App loads to login screen                     │
│                                                       │
│  ✓ Step 3: Verify no crashes                        │
│    └─ No error dialogs                              │
│    └─ UI is responsive                              │
│    └─ Login screen is visible                       │
│                                                       │
│  ✓ Step 4: Sign in with admin                       │
│    └─ Email: torryymsimango@gmail.com               │
│    └─ Password: torry123M                           │
│    └─ ✓ Login succeeds                              │
│                                                       │
│  ✓ Step 5: Verify admin dashboard loads             │
│    └─ Platform Admin Dashboard visible              │
│    └─ All sections show empty/no data               │
│    └─ ✓ SUCCESS                                     │
│                                                       │
└─────────────────────────────────────────────────────┘

⏱️  Time: 1 minute
✅ COMPLETE - Database reset successful!
```

---

## ⏱️ Total Time Required

```
┌─────────────────────────┬──────────────┐
│ Activity                │ Duration     │
├─────────────────────────┼──────────────┤
│ Phase 1: Preparation    │ 2 minutes    │
│ Phase 2: Execution      │ 1-2 minutes  │
│ Phase 3: Output Monitor │ (automatic)  │
│ Phase 4: Cache Cleanup  │ 30 seconds   │
│ Phase 5: App Restart    │ 1 minute     │
│ Phase 6: Verification   │ 1 minute     │
├─────────────────────────┼──────────────┤
│ TOTAL                   │ 5-7 minutes  │
└─────────────────────────┴──────────────┘
```

---

## 📊 Data Flow Diagram

```
┌──────────────────────────────────────────────────────────┐
│ BEFORE: Database State                                   │
├──────────────────────────────────────────────────────────┤
│                                                            │
│ Supabase Remote                │ Local Room Cache         │
│ ─────────────────────────────────────────────────────     │
│ • 5 groups                     │ • Cached groups          │
│ • 23 members                   │ • Cached members         │
│ • 47 contributions             │ • Cached contributions   │
│ • 12 payments                  │ • Cached data...         │
│ • ...other data...             │ • ...               │
│ • Multiple auth users          │ • Auth session           │
│                                                            │
└──────────────────────────────────────────────────────────┘
                            ↓
                    [Run Test Utility]
                            ↓
                    ┌───────────────┐
                    │   CLEARING    │
                    └───────────────┘
                            ↓
┌──────────────────────────────────────────────────────────┐
│ AFTER: Clean Database State                              │
├──────────────────────────────────────────────────────────┤
│                                                            │
│ Supabase Remote                │ Local Room Cache         │
│ ─────────────────────────────────────────────────────     │
│ • ✓ 0 groups                   │ • ✓ (cleared via        │
│ • ✓ 0 members                  │    adb shell pm clear)   │
│ • ✓ 0 contributions            │ • ✓ Fresh start          │
│ • ✓ 0 payments                 │ • ✓ Ready for testing    │
│ • ✓ 0 data...                  │ • ✓                      │
│ • ✓ 1 auth user (admin)        │ • ✓ Auth cleared         │
│                                                            │
└──────────────────────────────────────────────────────────┘
                            ↓
                    ┌───────────────┐
                    │   RECREATED   │
                    ├───────────────┤
                    │ Admin User    │
                    │ Email: ...    │
                    │ Password: ... │
                    └───────────────┘
```

---

## ✅ Success Checklist Visualization

```
Before Execution:
  [ ] Data backed up
  [ ] local.properties verified
  [ ] App closed
  [ ] Device/emulator ready

During Execution:
  [→] Test starts
  [✓] Phase 1: Clients initialized
  [✓] Phase 2: Tables cleared
  [✓] Phase 3: Admin user created
  [→] Console shows "DATABASE RESET COMPLETE"

After Execution:
  [✓] Local cache cleared (adb command)
  [✓] App restarted
  [✓] Login screen visible
  [✓] Admin credentials work
  [✓] Dashboard loads empty

Final Verification:
  [✓] Supabase tables are empty
  [✓] 1 admin user exists
  [✓] No old data visible
  [✓] App is pristine
  
═══════════════════════════════════════════════════
  ✅ ALL CHECKS PASSED - DATABASE RESET SUCCESSFUL!
═══════════════════════════════════════════════════
```

---

## 🔄 Decision Flow

```
                      START
                        ↓
            "Do I want to reset?"
                   /        \
                YES          NO
                /              \
              ↓                STOP
        Any concerns?          ↑
          /      \
        YES      NO
        /          \
       ↓            ↓
    Read docs    Backup?
    Checklist     /    \
       ↓        YES    NO
    Concerns?      ↓     ↓
      /  \       BACKUP SKIP
    NO    YES        ↓     ↓
    ↓      ↓         └─────┘
    Continue   Skip        ↓
    ↓                  READY
   EXECUTE            ↓
    ↓
  [Test Runs]
    ↓
  Success?
   /    \
 YES    NO
  ↓      ↓
 Clear   Debug
 Cache    ↓
  ↓     (See
 Restart checklist)
  ↓
VERIFY
  ↓
✅ COMPLETE
```

---

## 💡 Visual Tips

### Finding the Right File
```
Android Studio
    └─ Project pane (left side)
       └─ Expand: app
          └─ Expand: src
             └─ Expand: test
                └─ Expand: java
                   └─ com.sanibonani.save
                      └─ DatabaseResetUtility.kt ← HERE
```

### Finding the Right Method
```
DatabaseResetUtility.kt (open in editor)

    [Looking at code...]
    
    Find: resetDatabaseAndCreateAdmin()
          ↑ This is the main method to run
```

### Right-Click Context Menu
```
In the editor, place cursor on method name:

    resetDatabaseAndCreateAdmin()
              ↑ right-click here
    
    [Context Menu]
    ├─ Run 'resetDatabaseAndCreateAdmin()' ← Select this
    ├─ Debug 'resetDatabaseAndCreateAdmin()'
    ├─ Cut
    ├─ Copy
    └─ ...
```

---

## 🎯 Success Signals

```
✅ PHASE 1 SUCCESS
   └─ Console shows: "[1/4] Initializing Supabase clients..."
   └─ Console shows: "✓ Supabase clients initialized"

✅ PHASE 2 SUCCESS
   └─ Console shows: "[2/4] Clearing remote database tables..."
   └─ Console shows 8 ✓ marks (one per table)
   └─ Console shows: "✓ All data cleared from remote database"

✅ PHASE 3 SUCCESS
   └─ Console shows: "[3/4] Checking for existing admin user..."
   └─ Shows either: "✓ Deleted existing admin user" OR "Found none"

✅ PHASE 4 SUCCESS
   └─ Console shows: "[4/4] Creating Platform Admin user..."
   └─ Console shows: "✓ Platform Admin user created successfully!"
   └─ Shows email, role, password, and user ID

✅ FINAL SUCCESS
   └─ Console shows: "✓ DATABASE RESET COMPLETE"
   └─ No error messages
   └─ Exit code: 0
   └─ You can proceed to cleanup phase
```

---

## ❌ Failure Signals (What to Avoid)

```
❌ Test won't execute
   └─ Check: @Ignore annotation might need removal
   └─ Check: build.gradle has correct dependencies

❌ Connection timeout
   └─ Check: Internet connection
   └─ Check: Supabase URL is correct
   └─ Check: API keys are valid

❌ "Foreign key constraint" error
   └─ This is normal - utility handles it
   └─ Should show: ✓ Cleared despite error message

❌ "User already exists"
   └─ Utility tried to delete old one
   └─ Try running again or delete manually

❌ Process exit code: 1
   └─ Something failed
   └─ Scroll up to find error message
   └─ Refer to troubleshooting guide
```

---

## 📱 UI State During Execution

```
┌─────────────────────────────────┐
│ Execution in Progress            │
├─────────────────────────────────┤
│                                  │
│ [Android Studio]                 │
│ ┌──────────────────────────────┐ │
│ │ Run Window                    │ │
│ ├──────────────────────────────┤ │
│ │ [Running] resetDatabase...   │ │
│ │                              │ │
│ │ ▓▓▓▓▓▓▓░░░░░░░░░░░░ 40%    │ │
│ │                              │ │
│ │ [Execution Details]          │ │
│ │ • Connecting to Supabase...  │ │
│ │ • Clearing tables...         │ │
│ │ • Creating admin user...     │ │
│ │                              │ │
│ └──────────────────────────────┘ │
│                                  │
│ ⏱️  Elapsed: 1m 23s              │
│                                  │
└─────────────────────────────────┘
```

---

## 🎓 Common Questions Answered Visually

### Q: "What if I close the IDE while it's running?"
```
Answer:
  It's okay! The test will continue on Supabase's side.
  
  Restart the test to verify completion:
    └─ Check Supabase dashboard directly
    └─ Check if admin user was created
    └─ Re-run if needed (utility handles duplicates)
```

### Q: "Will the app break?"
```
Answer:
  No! The reset only affects data, not code.
  
  After reset:
    └─ App will work normally
    └─ All old data is gone
    └─ New admin user can sign in
    └─ Fresh state ready for testing
```

### Q: "Can I undo this?"
```
Answer:
  NOT RECOMMENDED - it's permanent.
  
  To recover:
    └─ You need a backup of the database
    └─ Or re-create data manually
    └─ This is why backups are important!
```

---

**Visual Guide Complete**  
**Ready to Execute**  
**Time to Reset: 5-7 minutes**

For text-based instructions, see: `DATABASE_RESET_EXECUTION_CHECKLIST.md`

