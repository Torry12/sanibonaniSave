# Database Reset Execution Checklist

## Pre-Execution (Before Running)

- [ ] I have backed up any critical data
- [ ] I understand this is **irreversible**
- [ ] I am NOT in a production environment (or have explicit authorization)
- [ ] My `local.properties` file contains valid Supabase credentials
- [ ] Android Studio / IntelliJ is open
- [ ] App is not currently running on device/emulator

---

## Execution Steps

### Step 1: Locate the Utility
- [ ] Navigate to: `app/src/test/java/com/sanibonani/save/DatabaseResetUtility.kt`
- [ ] File is found and opens without errors

### Step 2: Run the Main Reset Test
- [ ] Right-click on method: `resetDatabaseAndCreateAdmin()`
- [ ] Select: "Run 'resetDatabaseAndCreateAdmin()'"
- [ ] Wait for execution to complete (30-60 seconds)

### Step 3: Verify Successful Completion
- [ ] Console shows: `[1/4] Initializing Supabase clients...`
- [ ] Console shows: `[2/4] Clearing remote database tables...`
- [ ] Console shows: `[3/4] Checking for existing admin user...`
- [ ] Console shows: `[4/4] Creating Platform Admin user...`
- [ ] Console shows: `✓ DATABASE RESET COMPLETE`
- [ ] **NO error messages** in console output

### Step 4: Clear Local App Cache (Physical Device/Emulator)
One of the following:

**Option A: Via ADB Command**
```bash
adb shell pm clear com.sanibonani.save
```
- [ ] Run command in terminal
- [ ] No errors returned

**Option B: Via Device Settings**
- [ ] Open device Settings
- [ ] Navigate to: Settings > Apps > SanibonaniSave
- [ ] Tap: "Storage & cache"
- [ ] Tap: "Clear Cache"
- [ ] Tap: "OK" to confirm
- [ ] Navigate back to home screen

### Step 5: Restart the App
- [ ] Close SanibonaniSave completely (force stop if necessary)
- [ ] Wait 3 seconds
- [ ] Re-open SanibonaniSave
- [ ] App loads without crashing
- [ ] App is in initial/login state (no cached data)

---

## Post-Execution Verification

### Verify Supabase (Remote)
- [ ] Open: https://app.supabase.com
- [ ] Navigate to: Authentication > Users
- [ ] Verify: Only one user exists: `torryymsimango@gmail.com`
- [ ] Verify: User is marked as **Confirmed**
- [ ] Navigate to: SQL Editor
- [ ] Run: `SELECT COUNT(*) FROM groups;`
- [ ] Verify: Result is **0** (zero groups)

### Verify Admin User
- [ ] In app, tap: "Sign In"
- [ ] Email: `torryymsimango@gmail.com`
- [ ] Password: `torry123M`
- [ ] Tap: "Sign In"
- [ ] App loads Platform Admin Dashboard
- [ ] Verify: All sections show empty/no data

### Verify Local Database
- [ ] Open app Preferences/Database inspector (if available)
- [ ] Verify: No local cached data exists
- [ ] App should only show remote-synced data (which should be empty)

---

## Troubleshooting Checklist

### If Test Won't Run
- [ ] Verify `@Ignore` annotation presence
- [ ] Try: Right-click class name instead of method
- [ ] Try: Build project first (`Ctrl+F9`)
- [ ] Try: Invalidate caches (`File > Invalidate Caches > Invalidate and Restart`)

### If Supabase Connection Fails
- [ ] Verify internet connectivity
- [ ] Check `local.properties` for typos in:
  - [ ] `SUPABASE_URL`
  - [ ] `SUPABASE_SERVICE_ROLE_KEY`
- [ ] Verify Supabase project is still active in dashboard
- [ ] Try: Run `clearRemoteDataOnly()` alone to isolate issue

### If Admin Creation Fails
- [ ] Check Supabase Dashboard for existing `torryymsimango@gmail.com` user
- [ ] If exists: Manually delete it first
- [ ] Re-run `createAdminUserOnly()` test
- [ ] Verify email doesn't already exist in another project

### If App Crashes After Reset
- [ ] Try: `adb shell pm clear com.sanibonani.save` again
- [ ] Try: `adb uninstall com.sanibonani.save` + reinstall
- [ ] Check: `logcat` for specific error messages
- [ ] Try: Invalidate Android Studio caches and rebuild

### If Login Fails with Admin Credentials
- [ ] Verify email is exactly: `torryymsimango@gmail.com` (case-sensitive)
- [ ] Verify password is exactly: `torry123M` (case-sensitive)
- [ ] Check Supabase Dashboard > Users > Check user is **Confirmed**
- [ ] Try: Signing up as new user to test sign-in flow

---

## Success Indicators

✓ **You know it worked when:**
1. Console shows "DATABASE RESET COMPLETE"
2. Supabase Dashboard shows empty tables (0 groups, 0 members)
3. Supabase Users shows exactly 1 user (the admin)
4. App signs in successfully with admin credentials
5. Platform Admin Dashboard displays with no data

---

## Post-Reset Maintenance

### Keep Track Of:
- [ ] Time of reset (for your records)
- [ ] Who performed the reset
- [ ] Why the reset was performed
- [ ] Any data that was lost in the reset

### Next Steps:
- [ ] Notify team members if applicable
- [ ] Begin testing with clean database
- [ ] Create test data as needed
- [ ] Document any issues encountered

---

## Questions or Issues?

**Refer to:** `DATABASE_RESET_ADMIN_CREATION_GUIDE.md` for detailed troubleshooting

**Emergency Support:**
1. Keep the console output for debugging
2. Check Supabase dashboard for error messages
3. Review Kotlin error traces in IDE
4. Contact development team with screenshot of error

---

**Checklist Version:** 1.0  
**Last Updated:** April 16, 2026  
**Status:** ✓ Ready to Use

