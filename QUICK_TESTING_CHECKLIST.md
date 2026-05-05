# ✅ Quick Testing Checklist - SanibonaniSave

**Fast reference for executing functional testing phases**

---

## 🚀 Phase 1: Suspend Button Logic (20 minutes)

### ✅ Pre-Test: Code Review

**File**: `PlatformAdminViewModel.kt`
- [ ] Find method `updateGroupSuspensionState()` (should be ~15 lines)
- [ ] Check it updates BOTH:
  - [ ] `isPlatformSuspended`
  - [ ] `feeStatus`
- [ ] Verify no separate state updates

**File**: `PlatformAdminScreen.kt`
- [ ] Find All Groups tab UI code (~line 368)
- [ ] Check: `isSuspended = group.isPlatformSuspended || group.feeStatus == AdminFeeState.SUSPENDED`
- [ ] Verify button disabled during mutations
- [ ] Dialog reason reset: `remember(group.id) { mutableStateOf("") }`

### ✅ Unit Tests (2 minutes)

Run this command:
```bash
./gradlew test --tests "*PlatformAdminViewModel*"
```

Expected output:
```
✅ suspendGroup updates group list status immediately — PASSED
✅ unsuspendGroup restores group to paid state — PASSED
```

### ✅ Manual UI Testing (15 minutes)

1. **Login**
   - Email: `torrymsimango@gmail.com`
   - Password: `torry123M`
   - Navigate to: **All Groups** tab

2. **Test Suspend Action** (3 min)
   - [ ] Find a group with green fee chip (PAID)
   - [ ] Button says: "SUSPEND" (red)
   - [ ] Click button
   - [ ] Dialog appears
   - [ ] Type reason: "Test suspension"
   - [ ] Click "Confirm"
   - [ ] Wait for API response
   - [ ] Button changes to: "LIFT SUSPENSION" (green)
   - [ ] Fee chip changes to: red (SUSPENDED)

3. **Test Unsuspend Action** (3 min)
   - [ ] Click "LIFT SUSPENSION" button on same group
   - [ ] Dialog appears (if applicable)
   - [ ] Click "Confirm"
   - [ ] Button changes to: "SUSPEND" (red)
   - [ ] Fee chip changes to: green (PAID)

4. **Test Error Handling** (2 min)
   - [ ] Go offline (airplane mode or disable WiFi)
   - [ ] Try to suspend a group
   - [ ] Error message appears
   - [ ] Button returns to enabled
   - [ ] State NOT changed

5. **Test Dialog Reset** (2 min)
   - [ ] Click suspend on Group A
   - [ ] Type "Test reason"
   - [ ] Cancel
   - [ ] Click suspend on Group B
   - [ ] Reason field is EMPTY (not "Test reason")

6. **Test Double-Click Prevention** (2 min)
   - [ ] Click suspend
   - [ ] Quickly try to click again
   - [ ] Only one request sent (check network tab)
   - [ ] Button disabled during operation

### ✅ Result
- [ ] All manual tests passed
- [ ] Button and chip states synchronized
- [ ] No double-requests
- [ ] Error handling works
- **Status**: ✅ PASS / ❌ FAIL

---

## 🚀 Phase 2: Member Personas (60 minutes)

### ✅ Pre-Test: Database Check

```bash
# Run this SQL in Supabase
SELECT 
  u.email, 
  m.status_name, 
  COUNT(c.id) as contrib_count
FROM auth.users u
JOIN members m ON u.id = m.user_id
LEFT JOIN contributions c ON m.id = c.user_id
WHERE u.email IN ('member3@test.com', 'member4@test.com', 
                  'member5@test.com', 'member6@test.com')
GROUP BY u.email, m.status_name;
```

Expected result:
```
member3@test.com | ACTIVE   | 20+
member4@test.com | PROBATION | 3
member5@test.com | SUSPENDED| 12+
member6@test.com | PENDING_PAYMENT | 0
```

### ✅ Test Member 3: Nompumelelo (15 min)

**Login**: member3@test.com / password123

Tab Checklist:
- [ ] **Overview**: Shows 22-month member, R5k loan visible, 3 groups in switcher
- [ ] **Transactions**: Scroll through 18+ contributions, see partial payment somewhere
- [ ] **Loans**: Active loan R5,000, 6 repayments listed, next due date shown
- [ ] **Beneficiaries**: 3 people listed, names/ages visible, can view details
- [ ] **Documents**: ID and PoR show VERIFIED (green), can download
- [ ] **Multi-Group**: Switch to second group, contributions change, switch back
- [ ] **Profile**: Full address, phone, email, last 22 months visible

**Result**: ✅ PASS / ❌ FAIL

### ✅ Test Member 4: Sipho (12 min)

**Login**: member4@test.com / password123

Checklist:
- [ ] **Overview**: Yellow "PROBATION" badge visible
- [ ] **Timeline**: Shows probation end date (4-6 months from join)
- [ ] **Transactions**: Joining fee + 2 paid + 1 due month visible
- [ ] **Current Due**: Amount for this month shown prominently
- [ ] **Documents**: PoR shows PENDING (yellow), upload button present
- [ ] **Payment**: "Make Contribution" button enabled, correct amount shown

**Result**: ✅ PASS / ❌ FAIL

### ✅ Test Member 5: Busisiwe (12 min)

**Login**: member5@test.com / password123

Checklist:
- [ ] **Overview**: Red banner "ACCOUNT SUSPENDED"
- [ ] **Restrictions**: Can view all tabs but most actions blocked
- [ ] **Transactions**: 9 paid contributions visible, 3 overdue highlighted RED
- [ ] **Late Fee**: Line item showing late fee owed
- [ ] **Suspension Request**: "Request Lift Suspension" button present
- [ ] **Multi-Group**: Both groups show suspended state
- [ ] **Payment Button**: Disabled with reason "Account Suspended"

**Result**: ✅ PASS / ❌ FAIL

### ✅ Test Member 6: Mandla (8 min)

**Login**: member6@test.com / password123

Checklist:
- [ ] **Overview**: Red banner "COMPLETE PAYMENT"
- [ ] **Joining Fee**: Amount shown (e.g., R500)
- [ ] **Status**: "PENDING_PAYMENT" badge visible
- [ ] **Pay Now**: Button prominent, clicking goes to payment screen
- [ ] **Documents**: Locked, message says "Complete payment first"
- [ ] **After Payment**: 
  - [ ] Status changes to PROBATION
  - [ ] Documents unlocked
  - [ ] First contribution now due
  - [ ] Welcome notification appears

**Result**: ✅ PASS / ❌ FAIL

---

## 🚀 Phase 3: Analytics Dashboard (20 minutes)

**Login**: torrymsimango@gmail.com / torry123M  
Navigate to: **Analytics** tab

### ✅ Metrics Verification

- [ ] **Groups**: Total = 30 (matches seeder count)
- [ ] **Members**: Total = 300+ (matches seeder count)
- [ ] **Breakdown**: Shows counts by status (ACTIVE, PROBATION, SUSPENDED, PENDING)
- [ ] **Fee Status**: Shows PAID vs SUSPENDED counts
- [ ] **Revenue**: Total platform fees collected displayed
- [ ] **Trends**: Month-over-month chart shows activity

### ✅ All Groups Tab

Navigate to: **All Groups** tab

- [ ] **Listing**: All 30 groups visible (paginated or scrollable)
- [ ] **Counts**: Each group shows member count
- [ ] **Search**: Type "Johannesburg", only matching groups shown
- [ ] **Filter by Type**: Select "Burial Society", count updates
- [ ] **Filter by Status**: Select "SUSPENDED", shows suspended groups only
- [ ] **Sort**: Different sort options work smoothly

### ✅ Fee Management

Navigate to: **Fee Management** tab

- [ ] **Current Fees**: Platform fee (R700), admin fee percentage shown
- [ ] **Update**: Change a fee value, save, confirmation appears
- [ ] **Validation**: Try invalid value (e.g., 101% admin fee), blocked
- [ ] **Audit**: Fee change logged with timestamp

**Result**: ✅ PASS / ❌ FAIL

---

## 🚀 Phase 4: RLS & Access Control (15 minutes)

### ✅ Platform Admin Access

**Login**: torrymsimango@gmail.com / torry123M

- [ ] Can access: Analytics, All Groups, Fee Management, Payouts, Maintenance
- [ ] Can see: All 30 groups, all 300+ members
- [ ] Can perform: Suspend group, update fees, approve payouts

### ✅ Group Admin Access

**Login**: admin2@test.com / password123

- [ ] Can see: Only their own group (NOT all 30)
- [ ] Cannot access: Analytics, Fee Management, Platform Admin functions
- [ ] Can manage: Members in their group, settings, payouts
- [ ] Restriction test: Try direct URL to access another group → Blocked

### ✅ Member Access

**Login**: member1@test.com / password123

- [ ] Can access: Member portal only
- [ ] Cannot see: Group list, all members
- [ ] If multi-group: Dropdown shows only their groups
- [ ] Cannot access: Admin functions, analytics

### ✅ Data Isolation

- [ ] Member 1 view Member's group: ✓
- [ ] Member 1 try Member's contribution view another member: ✗ (blocked)
- [ ] Admin can view their group members: ✓
- [ ] Admin try to view other admin group: ✗ (blocked)

**Result**: ✅ PASS / ❌ FAIL

---

## 🚀 Phase 5: Database Integrity (30 minutes)

Run these SQL queries in Supabase:

### ✅ Check 1: Group Count
```sql
SELECT COUNT(*) as total_groups FROM groups;
```
**Expected**: 30

### ✅ Check 2: Member Count
```sql
SELECT COUNT(*) as total_members FROM members;
```
**Expected**: 300+

### ✅ Check 3: Persona Existence
```sql
SELECT email, COUNT(*) as record_count 
FROM auth.users 
WHERE email IN ('member3@test.com', 'member4@test.com', 
                'member5@test.com', 'member6@test.com')
GROUP BY email;
```
**Expected**: 4 rows, 1 record each

### ✅ Check 4: Nompumelelo Contributions
```sql
SELECT COUNT(*) as contrib_count 
FROM contributions 
WHERE user_id IN (SELECT id FROM auth.users WHERE email = 'member3@test.com');
```
**Expected**: 20+

### ✅ Check 5: Nompumelelo Beneficiaries
```sql
SELECT COUNT(*) as beneficiary_count 
FROM beneficiaries 
WHERE user_id IN (SELECT id FROM auth.users WHERE email = 'member3@test.com');
```
**Expected**: 3

### ✅ Check 6: No Orphaned Contributions
```sql
SELECT COUNT(*) FROM contributions 
WHERE user_id NOT IN (SELECT id FROM members);
```
**Expected**: 0

### ✅ Check 7: No Orphaned Beneficiaries
```sql
SELECT COUNT(*) FROM beneficiaries 
WHERE user_id NOT IN (SELECT id FROM members);
```
**Expected**: 0

### ✅ Check 8: Member Count Accuracy
```sql
SELECT g.id, g.name, g.current_members, COUNT(m.id) as actual
FROM groups g
LEFT JOIN members m ON g.id = m.group_id AND m.status_name NOT IN ('SUSPENDED')
GROUP BY g.id, g.name, g.current_members
LIMIT 5;
```
**Expected**: current_members = actual for all groups

**Result**: ✅ ALL PASS / ❌ SOME FAILED

---

## 📊 Summary Table

| Phase | Duration | Status | Issues |
|-------|----------|--------|--------|
| 1. Suspend Button | 20 min | ⬜ | |
| 2. Member Personas | 60 min | ⬜ | |
| 3. Analytics | 20 min | ⬜ | |
| 4. RLS/Access | 15 min | ⬜ | |
| 5. Database | 30 min | ⬜ | |
| **TOTAL** | **~2 hours** | **⬜** | |

---

## 🎯 Final Sign-Off

Once all testing complete, verify:

- [ ] All 5 phases passed
- [ ] No critical issues found
- [ ] All personas working as expected
- [ ] Suspend/unsuspend atomic and working
- [ ] No data leakage
- [ ] Database integrity maintained
- [ ] Ready for production

**Overall Status**: ⬜ PENDING / 🟢 PASS / 🔴 FAIL

---

## 🆘 Quick Troubleshooting

| Issue | Solution |
|-------|----------|
| Suspend button not changing | Check `updateGroupSuspensionState()` is being called |
| Persona login fails | Verify seeder ran, use exact emails from AGENTS.md |
| No groups visible | Run seeder: `seed_30_groups_300_members.sql` |
| UI shows old data | Refresh page, check Flow is collecting with `collectAsState()` |
| DB queries return 0 | Check connected to right environment |
| Member can see other groups | RLS policy might be broken, check `create_rls_policies()` |

---

*Quick Checklist v1 - May 4, 2026*  
*Use this alongside: FUNCTIONAL_TESTING_VERIFICATION_PLAN.md*

