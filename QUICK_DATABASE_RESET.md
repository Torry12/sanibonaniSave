# Quick Database Reset Reference

## TL;DR - 2 Minute Reset

### Step 1: Run Test (60 seconds)
- Open: `app/src/test/java/com/sanibonani/save/DatabaseResetUtility.kt`
- Right-click: `resetDatabaseAndCreateAdmin()`
- Click: "Run"
- ✓ Wait for console to show "DATABASE RESET COMPLETE"

### Step 2: Clear App Cache (30 seconds)
```bash
adb shell pm clear com.sanibonani.save
```

### Step 3: Restart App (30 seconds)
- Close SanibonaniSave completely
- Re-open it
- All data is gone, admin is ready to use

---

## Login After Reset

```
Email: torryymsimango@gmail.com
Password: torry123M
```

You'll be logged in as **Platform Admin**.

---

## What Was Deleted

✓ All groups (Supabase)  
✓ All members (Supabase)  
✓ All contributions/payments (Supabase)  
✓ All local app cache (Room database)  
✓ All auth users except new admin

---

## Alternative: Partial Resets

**Clear data only (keep existing users):**
```
Run: clearRemoteDataOnly()
```

**Create admin only (keep existing data):**
```
Run: createAdminUserOnly()
```

---

## If Something Goes Wrong

1. **Test won't run:** Remove `@Ignore` annotation temporarily
2. **Admin already exists:** Manually delete in Supabase Dashboard → Users
3. **App still shows old data:** Run `adb shell pm clear com.sanibonani.save`
4. **Connection error:** Check `local.properties` has valid Supabase keys

---

**Full guide:** See `DATABASE_RESET_ADMIN_CREATION_GUIDE.md`

