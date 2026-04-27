# Database Reset & Admin Creation Guide

**SanibonaniSave** — Clear and Reset Database with Admin User Creation

---

## Overview

This guide provides step-by-step instructions to:
1. **Clear all remote Supabase data** (all tables)
2. **Clear local Room database cache**
3. **Create a Platform Admin user** with the specified credentials

---

## ⚠️ WARNING: DESTRUCTIVE OPERATION

This process **PERMANENTLY DELETES** all data in:
- All Supabase tables (groups, members, contributions, payments, payouts, etc.)
- Local Room database cache

**This is irreversible.** Ensure you have backups of any critical data before proceeding.

---

## Admin Credentials Created

After running this utility, you will have:

```
Email: torryymsimango@gmail.com
Password: torry123M
Role: platform_admin
```

These are the credentials specified in `AGENTS.md` for the Platform Administrator.

---

## Method 1: Full Reset via IDE Test Runner (Recommended)

### Step 1: Open the Test Class

Navigate to:
```
app/src/test/java/com/sanibonani/save/DatabaseResetUtility.kt
```

### Step 2: Run the Test

**In Android Studio / IntelliJ:**

1. Right-click on the method: `resetDatabaseAndCreateAdmin()`
2. Select **Run 'resetDatabaseAndCreateAdmin'**

Or press: `Ctrl+Shift+F10` (Windows) with the cursor on the method.

### Step 3: Monitor Console Output

The utility will print progress:

```
================================================================================
SANIBONANI SAVE - DATABASE RESET & ADMIN CREATION UTILITY
================================================================================

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
   User ID: <uuid>

================================================================================
✓ DATABASE RESET COMPLETE
================================================================================

Admin Login Credentials:
  Email: torryymsimango@gmail.com
  Password: torry123M

Next Steps:
  1. Clear local app cache: Settings > Apps > SanibonaniSave > Clear Cache
  2. Close and restart the app
  3. Sign in with the admin credentials above
================================================================================
```

### Step 4: Clear Local Cache

After the test completes, you must also clear the local Room database:

**Option A: Via Settings (Physical Device/Emulator)**
```
Settings > Apps > SanibonaniSave > Storage > Clear Cache
```

**Option B: Via ADB Command**
```bash
adb shell pm clear com.sanibonani.save
```

### Step 5: Restart App

Close and reopen **SanibonaniSave** to trigger fresh database initialization.

---

## Method 2: Clear Remote Data Only (without admin creation)

If you only want to clear remote data:

```
Right-click: clearRemoteDataOnly()
Select: Run 'clearRemoteDataOnly'
```

---

## Method 3: Create Admin User Only (without clearing data)

If you only want to create the admin user:

```
Right-click: createAdminUserOnly()
Select: Run 'createAdminUserOnly'
```

---

## Post-Reset Verification

After completing the above steps, verify the reset:

### 1. **Remote Database**

Sign in to **Supabase Dashboard**:
- URL: https://app.supabase.com/projects
- Check that all tables are empty (except for structure/schema)

### 2. **Admin User in Auth**

In Supabase Dashboard → Authentication → Users:
- Should see exactly one user: `torryymsimango@gmail.com`
- Status: **Confirmed** ✓
- Role metadata: `platform_admin`

### 3. **Local App**

1. Sign in with admin credentials
2. Should see **Platform Admin Dashboard**
3. All data should be pristine (no existing groups, members, etc.)

---

## Troubleshooting

### Error: "Test runs but nothing happens"

**Cause:** `@Ignore` annotation prevents test execution
**Solution:** Temporarily remove `@Ignore` annotation before running

```kotlin
// Remove or comment out:
// @Ignore("Manual utility, not a unit test - DESTRUCTIVE OPERATION")
@Test
fun resetDatabaseAndCreateAdmin() = runBlocking {
    // ...
}
```

After running, **add it back** to prevent accidental execution.

### Error: "Service role key invalid" or "Permission denied"

**Cause:** Invalid credentials or changed Supabase settings
**Solution:** 
1. Check `local.properties` for correct `SUPABASE_SERVICE_ROLE_KEY`
2. Verify Supabase project is still active
3. Confirm you're using the correct Supabase URL

### Error: "Foreign key constraint violation"

**Cause:** Tables not cleared in the correct order
**Solution:** The utility automatically handles this by clearing child tables first

### Admin Creation Fails: "Email already registered"

**Cause:** Previous admin account still exists
**Solution:** The utility attempts to delete it automatically. If it fails:

1. Go to Supabase Dashboard → Authentication → Users
2. Manually delete the user `torryymsimango@gmail.com`
3. Re-run the utility

---

## What Gets Reset

### Remote (Supabase)

| Table | Action |
|-------|--------|
| `groups` | All records deleted |
| `members` | All records deleted |
| `contributions` | All records deleted |
| `payments` | All records deleted |
| `beneficiaries` | All records deleted |
| `notifications` | All records deleted |
| `payouts` | All records deleted |
| `member_documents` | All records deleted |

### Local (Room Database)

All tables are cleared when you run "Clear Cache" or use the in-app admin panel.

### Auth

- Previous admin user (if any) is deleted
- New admin user `torryymsimango@gmail.com` is created

---

## Advanced: Custom Credentials

To use different admin credentials, edit `DatabaseResetUtility.kt`:

```kotlin
val adminEmail = "your-email@example.com"  // Change this
val adminPassword = "YourPassword123"      // Change this
```

Then re-run the test.

---

## What NOT to Do

❌ **Do NOT** run this in production without explicit authorization  
❌ **Do NOT** forget to back up data before running  
❌ **Do NOT** share the test file with credentials in version control  
❌ **Do NOT** run this multiple times rapidly (can cause rate-limiting)

---

## Related Documentation

- **AGENTS.md**: Contains admin credentials reference
- **SanibonaniDatabase.kt**: Room database structure
- **SupabaseManager.kt**: Supabase initialization
- **AdminViewModel.kt**: Contains `resetLocalData()` function for UI-level reset

---

## Support

For issues or questions:
1. Check the **Troubleshooting** section above
2. Review console output for specific error messages
3. Verify `local.properties` contains correct Supabase credentials
4. Ensure network connectivity to Supabase

---

**Last Updated:** April 16, 2026  
**Version:** 1.0

