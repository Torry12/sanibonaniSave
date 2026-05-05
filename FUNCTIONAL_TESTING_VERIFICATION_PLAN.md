# 🧪 SanibonaniSave Functional Testing Verification Plan

**Comprehensive validation for suspend/unsuspend fixes, persona member data, and end-to-end app functionality**

*Date: May 4, 2026*  
*Focus: Validate all fixes from previous session + 5-phase systematic testing*

---

## 📋 Executive Summary

This plan systematically validates:

✅ **Phase 1: Suspend Button Logic Fixes** - Verify state synchronization between `isPlatformSuspended` and `feeStatus`  
✅ **Phase 2: Member Persona Journeys** - Test all 4 named personas with realistic financial histories  
✅ **Phase 3: Platform Admin Analytics** - Validate 30 groups, 300 members, analytics dashboard  
✅ **Phase 4: RLS/Authentication** - Verify role-based access control and data isolation  
✅ **Phase 5: Data Validation** - Database integrity checks and seeder validation  

---

## 🎯 Phase 1: Suspend Button Logic Fixes (HIGH PRIORITY)

**Goal**: Verify suspend/unsuspend button state synchronization fixes work end-to-end

### 1.1 Code Review Verification
- [ ] `PlatformAdminViewModel::updateGroupSuspensionState()` exists and updates both flags atomically
- [ ] `PlatformAdminScreen` computes effective suspension as: `isSuspended = group.isPlatformSuspended || group.feeStatus == AdminFeeState.SUSPENDED`
- [ ] Suspend button disabled during mutations
- [ ] Unsuspend button disabled during mutations
- [ ] Dialog reason state reset per group selection
- [ ] Unit tests pass: `suspendGroup` and `unsuspendGroup` test cases

**Test Commands**:
```bash
# Run only suspend/unsuspend tests
./gradlew test --tests "*PlatformAdminViewModel*"

# Compile check
./gradlew :app:compileDebugKotlin
```

**Expected Output**:
- ✅ All PlatformAdminViewModel tests pass
- ✅ No compilation errors
- ✅ Both `isPlatformSuspended` and `feeStatus` change together

---

### 1.2 Manual UI Testing - Platform Admin All Groups Tab

**Setup**:
1. Login as Platform Admin (torrymsimango@gmail.com / torry123M)
2. Navigate to "All Groups" tab
3. Load 30 seeded groups

**Test Cases**:

#### Test 1.2.1: Button State Rendering for Paid Groups
- **Action**: Scroll to first group with `feeStatus == PAID`
- **Expected Result**:
  - Button shows: "SUSPEND" (red color)
  - Fee chip shows: "PAID" (green background)
  - Button is enabled (clickable)
- **Verify**: UI consistent across all PAID groups

#### Test 1.2.2: Button State Rendering for Suspended Groups
- **Action**: Scroll to first group with `feeStatus == SUSPENDED`
- **Expected Result**:
  - Button shows: "LIFT SUSPENSION" (green color)
  - Fee chip shows: "SUSPENDED" (red background)
  - Button is enabled (clickable)
- **Verify**: UI consistent across all SUSPENDED groups

#### Test 1.2.3: Suspend Action - Dialog & Confirmation
- **Action**: 
  1. Click "SUSPEND" button on a PAID group
  2. Type suspension reason (e.g., "Rule violation")
  3. Click "Confirm"
- **Expected Result**:
  - Dialog opens with reason input field
  - Confirm button disabled until reason entered (non-blank)
  - Button shows loading state during operation
  - Dialog closes on success
  - Group card refreshes with new state

#### Test 1.2.4: Suspend Action - State Update Verification
- **Action**: After suspension succeeds, verify UI state
- **Expected Result**:
  - Button text changed to "LIFT SUSPENSION" (green)
  - Fee chip shows "SUSPENDED" (red)
  - `feeStatus` in database is `SUSPENDED`
  - `isPlatformSuspended` in database is `true`
- **Verify**: Both database flags changed together

#### Test 1.2.5: Unsuspend Action - Button Click
- **Action**:
  1. Find a SUSPENDED group
  2. Click "LIFT SUSPENSION" button
- **Expected Result**:
  - Button shows loading state
  - Group refreshes
  - Button text changed to "SUSPEND" (red)
  - Fee chip shows "PAID" (green)

#### Test 1.2.6: Unsuspend Action - State Update Verification
- **Action**: After unsuspend succeeds, verify UI state
- **Expected Result**:
  - Button text changed to "SUSPEND" (red)
  - Fee chip shows "PAID" (green)
  - `feeStatus` in database is `PAID`
  - `isPlatformSuspended` in database is `false`
- **Verify**: Both database flags changed together

#### Test 1.2.7: Form Validation - Reason Field
- **Action**:
  1. Click "SUSPEND" on a group
  2. Try to click "Confirm" without entering reason
- **Expected Result**:
  - Confirm button remains disabled
  - Reason input shows error state (if empty)
  - Message says "Enter a reason"

#### Test 1.2.8: Dialog Reset Per Group
- **Action**:
  1. Click "SUSPEND" on group A
  2. Enter reason "Test"
  3. Click "Cancel"
  4. Click "SUSPEND" on group B
- **Expected Result**:
  - Dialog reason field is EMPTY (not "Test")
  - Each group gets fresh dialog state

#### Test 1.2.9: Loading State & Button Disable
- **Action**:
  1. Click "SUSPEND"
  2. Immediately click again (before completion)
- **Expected Result**:
  - Button disabled during operation (prevents double-click)
  - Loading spinner visible
  - Only one request sent

#### Test 1.2.10: Error Handling
- **Action**: Intentionally trigger error (offline, network failure, etc.)
- **Expected Result**:
  - Error message displayed
  - Button returns to enabled state
  - State NOT changed in UI (rollback)

---

### 1.3 Integration Testing - Suspend/Unsuspend State Persistence

**Test**: Database state consistency after suspend/unsuspend

```bash
./gradlew connectedAndroidTest --tests "*PlatformAdmin*" --info
```

**Verification Steps**:
1. Run suspend action
2. Query database:
   ```sql
   SELECT id, is_platform_suspended, fee_status 
   FROM groups 
   WHERE id = 'suspended-group-id';
   ```
3. Verify both columns changed together
4. Restart app, reload group, verify state persists
5. Repeat for unsuspend

---

## 📊 Phase 2: Member Persona Journeys (HIGH PRIORITY)

**Goal**: Validate all 4 test personas with realistic financial histories work correctly

### 2.1 Test Data Verification

**Personas to Test**:
1. **Member 3 - Nompumelelo Dlamini** (member3@test.com)
   - 22-month member (active senior)
   - 18+ paid contributions + 1 partial
   - Active loan (R5,000) with 6 repayments
   - 3 beneficiaries (Burial Society)
   - Multi-group member (3 groups)

2. **Member 4 - Sipho Radebe** (member4@test.com)
   - 2-month probation member
   - Joining fee + 2 paid + 1 current month due
   - Proof of Residence pending
   - Single group

3. **Member 5 - Busisiwe Mthembu** (member5@test.com)
   - Suspended member
   - 9 paid contributions
   - 3 consecutive overdue months
   - Late fee outstanding
   - 2 groups

4. **Member 6 - Mandla Sithole** (member6@test.com)
   - Brand new pending payment
   - No contributions yet
   - Joined 5 days ago
   - Joining fee due

### 2.2 Database Verification - Persona Data

**Test Command**:
```sql
-- Verify Nompumelelo exists
SELECT u.id, u.email, m.status, m.total_paid, 
       COUNT(*) as group_count
FROM auth.users u
JOIN members m ON u.id = m.user_id
WHERE u.email IN ('member3@test.com', 'member4@test.com', 
                  'member5@test.com', 'member6@test.com')
GROUP BY u.id, u.email, m.status, m.total_paid;

-- Verify Nompуmelelo loans
SELECT user_id, amount, status, repayments_made
FROM loans
WHERE user_id IN (
  SELECT user_id FROM members WHERE status_name = 'member3@test.com'
);

-- Verify Nompumelelo beneficiaries
SELECT user_id, name, age, date_added
FROM beneficiaries
WHERE user_id IN (
  SELECT user_id FROM members WHERE email = 'member3@test.com'
);
```

### 2.3 UI Journey - Nompumelelo (Senior Member)

**Login**: member3@test.com / password123

**Test Cases**:

#### Test 2.3.1: Overview Tab - Displays Senior Status
- [ ] Profile shows "22-month member" or similar
- [ ] Active loan widget visible with R5,000 amount
- [ ] Next repayment date displayed
- [ ] 3 groups shown in group switcher

#### Test 2.3.2: Transactions Tab - 20+ Contribution Records
- [ ] Scrollable list of 18+ paid + 1 partial contributions
- [ ] Each shows date, amount, status
- [ ] Partial contribution shows reduced amount
- [ ] CSV/PDF export buttons functional

#### Test 2.3.3: Loans Tab - Active Loan & Repayments
- [ ] Active loan displayed: R5,000
- [ ] Loan status: "ACTIVE" or "IN_REPAYMENT"
- [ ] 6 repayment records visible
- [ ] Repayment calendar shows schedule
- [ ] Next repayment date highlighted
- [ ] Option to make early repayment available

#### Test 2.3.4: Beneficiaries Tab - 3 Dependents
- [ ] All 3 beneficiaries listed
- [ ] Names, ages, birthdate displayed
- [ ] Can add/edit/delete beneficiaries (if max not reached)
- [ ] Contribution adjustment shown (if burial society)
- [ ] Over-65 age indicators (if applicable)

#### Test 2.3.5: Multi-Group Switching
- [ ] Group switcher dropdown shows 3 groups
- [ ] Current group highlighted with checkmark
- [ ] Switching group clears local data (no leakage)
- [ ] Transactions change for new group
- [ ] Loans update (if different amounts in each group)
- [ ] Beneficiaries reset if different per group

#### Test 2.3.6: Documents Tab - Verification Status
- [ ] ID document shows VERIFIED (green check)
- [ ] Proof of Residence shows VERIFIED (green check)
- [ ] Beneficiary form shows status
- [ ] Can download verified documents
- [ ] Sections locked once verified

#### Test 2.3.7: Profile Tab - Complete Member Info
- [ ] All personal data displayed correctly
- [ ] Address matches seeder data
- [ ] Phone and email correct
- [ ] Membership dates show 22 months
- [ ] Status shows "ACTIVE"
- [ ] Profile photo loads (if present)

---

### 2.4 UI Journey - Sipho (Probation New Member)

**Login**: member4@test.com / password123

**Test Cases**:

#### Test 2.4.1: Overview Tab - Probation Status
- [ ] Status badge shows "PROBATION" (yellow)
- [ ] Probation end date displayed
- [ ] Countdown to becoming ACTIVE shown
- [ ] Current month payment due indicator visible
- [ ] "Make Payment" button prominent

#### Test 2.4.2: Payment Status Widget
- [ ] Shows current month amount due
- [ ] Shows last payment date (2 months ago)
- [ ] Shows next due date (this month)
- [ ] Button text: "Make Contribution"

#### Test 2.4.3: Transactions Tab - 2 Paid + Current Due
- [ ] Joining fee showing as first contribution
- [ ] 2 monthly contributions showing as PAID
- [ ] Current month showing as DUE or OVERDUE (0-30 days)
- [ ] Status indicators color-coded

#### Test 2.4.4: Documents Tab - PoR Pending
- [ ] Proof of Residence shows PENDING (yellow)
- [ ] "Upload" button available
- [ ] Other docs show PENDING state
- [ ] Upload documentation flow functional

#### Test 2.4.5: Probation Calculation
- [ ] Joined 2 months ago
- [ ] Probation period calculated correctly
- [ ] End date should be approximately 4-6 months from join
- [ ] Countdown accurate
- [ ] On probation end date, status transitions to ACTIVE

---

### 2.5 UI Journey - Busisiwe (Suspended Member)

**Login**: member5@test.com / password123

**Test Cases**:

#### Test 2.5.1: Overview Tab - Suspension Notice
- [ ] Large red banner: "ACCOUNT SUSPENDED"
- [ ] Reason displayed (if provided)
- [ ] Action button: "Request Lift Suspension" or similar
- [ ] Cannot access most tabs (read-only mode)

#### Test 2.5.2: Tab Navigation - Restricted Access
- [ ] Overview tab: ACCESSIBLE (read-only)
- [ ] Transactions tab: ACCESSIBLE (read-only)
- [ ] Loans tab: ACCESSIBLE (read-only)
- [ ] Beneficiaries tab: ACCESSIBLE (read-only)
- [ ] Documents tab: ACCESSIBLE (read-only)
- [ ] Messages tab: ACCESSIBLE (report issue to admin)
- [ ] Notifications tab: ACCESSIBLE (read-only)
- [ ] Profile tab: RESTRICTED or READ-ONLY
- [ ] "Make Payment" button: DISABLED

#### Test 2.5.3: Transactions Tab - Overdue Records
- [ ] 9 paid contributions visible
- [ ] 3 overdue months shown in RED color
- [ ] Late fee line item visible
- [ ] Total amount due displayed
- [ ] Payment button disabled with reason

#### Test 2.5.4: Suspension Request Flow
- [ ] "Request Lift" button visible on overview
- [ ] Click opens dialog
- [ ] Compose message explaining situation
- [ ] Submit sends message to group admin
- [ ] Confirmation shown: "Request sent"
- [ ] Follow-up notification appears when admin responds

#### Test 2.5.5: 2 Groups Visibility
- [ ] Group switcher shows 2 groups
- [ ] Suspension applies across both (if platform-suspended)
- [ ] Or per-group suspension (if different status per group)
- [ ] Switching groups maintains suspension state correctly

---

### 2.6 UI Journey - Mandla (Brand New Member)

**Login**: member6@test.com / password123

**Test Cases**:

#### Test 2.6.1: Registration Flow
- [ ] Member created 5 days ago
- [ ] Status: PENDING_PAYMENT
- [ ] Large payment notice: "Complete Your Registration"
- [ ] Joining fee amount displayed (e.g., R500)
- [ ] Payment deadline shown (if applicable)

#### Test 2.6.2: Payment Required Widget
- [ ] Cannot access most features until payment made
- [ ] Payment instructions clear
- [ ] "Pay Now" button prominent
- [ ] Amount and group name shown
- [ ] Status: "Payment Pending"

#### Test 2.6.3: Document Section Locked
- [ ] Document upload section shows greyed out
- [ ] Message: "Complete payment to upload documents"
- [ ] Archive link instead to documentation examples

#### Test 2.6.4: Payment Screen - Joining Fee
- [ ] Navigate to payment or click "Pay Now"
- [ ] Amount matches joining fee (e.g., R500)
- [ ] Payment type shows "Joining Fee" (not "Contribution")
- [ ] Payment method options shown (Yoco, Bank Transfer)
- [ ] Complete payment process

#### Test 2.6.5: Post-Payment Status Transition
- [ ] After successful payment:
  - [ ] Status changes to PROBATION or ACTIVE (based on group rules)
  - [ ] Overview tab updates immediately
  - [ ] Payment badge disappears
  - [ ] Document upload unlocked
  - [ ] Transactions tab shows joining fee as first contribution
  - [ ] Notifications show welcome message

#### Test 2.6.6: First Contribution Available
- [ ] After payment, current month contribution becomes due
- [ ] "Make Contribution" button enabled
- [ ] Payment screen allows regular contribution (e.g., R200)
- [ ] Amount is for current month, not joining fee

---

## 📈 Phase 3: Platform Admin Analytics Dashboard (MEDIUM PRIORITY)

**Goal**: Validate analytics, group discovery, and fee management

### 3.1 Analytics Dashboard Verification

**Login**: torrymsimango@gmail.com / torry123M  
**Navigate**: Analytics tab

**Test Cases**:

#### Test 3.1.1: Group Count Statistics
- [ ] Total groups: 30 (from seeder)
- [ ] Groups by type breakdown (Burial Society, Stokvel, ROSCA)
- [ ] Groups by status (ACTIVE, SUSPENDED, PENDING)
- [ ] Trends chart shows growth over time

#### Test 3.1.2: Member Count Statistics
- [ ] Total members: 300 (from seeder)
- [ ] Members by status (ACTIVE, PROBATION, PENDING_PAYMENT, SUSPENDED)
- [ ] Total contributions calculated
- [ ] Total payouts tracked
- [ ] Average member age per group

#### Test 3.1.3: Financial Metrics
- [ ] Total platform fees collected
- [ ] Total group balances summed
- [ ] Average contribution per member
- [ ] Total loans outstanding
- [ ] Default rate (if applicable)

#### Test 3.1.4: Fee Chart & Trends
- [ ] Revenue by month displayed
- [ ] Trending indicators (up/down arrows)
- [ ] Forecasting for next month
- [ ] Comparison to previous month

---

### 3.2 All Groups Tab - Search & Filter

**Test Cases**:

#### Test 3.2.1: Group Listing
- [ ] All 30 groups displayed
- [ ] Pagination or infinite scroll functional
- [ ] Each group card shows name, type, fee status, member count

#### Test 3.2.2: Search Functionality
- **Action**: Search by group name (e.g., "Durban")
- **Expected**: Only groups with "Durban" in name shown

#### Test 3.2.3: Filter by Type
- [ ] Filter by "Burial Society"
- [ ] Show only burial society groups
- [ ] Count matches expected

#### Test 3.2.4: Filter by Fee Status
- [ ] Filter by "PAID" - show only non-suspended
- [ ] Filter by "SUSPENDED" - show only suspended
- [ ] Filter by "PENDING" - show only pending

#### Test 3.2.5: Filter by Location
- [ ] Filter by "Johannesburg", "Durban", "Cape Town"
- [ ] Groups pinned correctly on map (if map view available)
- [ ] Geohash search working

#### Test 3.2.6: Sort Options
- [ ] Sort by name (A-Z, Z-A)
- [ ] Sort by member count (ascending, descending)
- [ ] Sort by balance (high to low)
- [ ] Sort by creation date (newest first)

---

### 3.3 Fee Management Tab

**Test Cases**:

#### Test 3.3.1: Global Fee Structure
- [ ] Platform registration fee: R700
- [ ] Admin fee percentage (e.g., 5%)
- [ ] Minimum group size to activate
- [ ] Display update last modified timestamp

#### Test 3.3.2: Update Fees
- **Action**: Change admin fee from 5% to 6%
- [ ] Input field accepts new value
- [ ] Validation: must be 0-100
- [ ] "Save" button functional
- [ ] Confirmation message shown
- [ ] New fee applies to future groups (old groups unaffected)

#### Test 3.3.3: Effective Dates
- [ ] Changes take effect immediately or on specified date
- [ ] Grandfathering applies to existing groups (if configured)
- [ ] Log shows who updated fees and when

---

## 🔐 Phase 4: RLS & Authentication Testing (MEDIUM PRIORITY)

**Goal**: Verify role-based access control and data isolation

### 4.1 Platform Admin vs Group Admin vs Member

**Test Cases**:

#### Test 4.1.1: Platform Admin Scope
- **Login**: torrymsimango@gmail.com / torry123M
- [ ] Can view all groups (30)
- [ ] Can view all members (300)
- [ ] Can access all admin functions
- [ ] Can suspend/unsuspend any group
- [ ] Can view all payouts
- [ ] Cannot modify individual member profiles (read-only or limited)

#### Test 4.1.2: Group Admin Scope
- **Login**: admin2@test.com / password123 (Group Admin 2)
- [ ] Can view ONLY their own group
- [ ] Can view only members in their group
- [ ] Cannot switch to other groups
- [ ] Cannot see groups they don't administer
- [ ] Can manage settings for their group only
- [ ] Can request payouts for their group only
- [ ] Cannot access Platform Admin functions

#### Test 4.1.3: Regular Member Scope
- **Login**: member1@test.com / password123
- [ ] Can view only their memberships
- [ ] Can view only groups they belong to (multi-group sees switcher)
- [ ] Cannot see other members' data
- [ ] Cannot access admin functions
- [ ] Cannot view all groups list
- [ ] Cannot access platform admin features

---

### 4.2 Data Isolation Tests

**Test Cases**:

#### Test 4.2.1: Member Cannot Access Other Group Data
- **Setup**: Member1 belongs to Group A only
- **Action**: Try to access Group B member list
- [ ] Request blocked by RLS policy
- [ ] Error message: "Unauthorized" or redirect to home
- [ ] No member data from Group B visible

#### Test 4.2.2: Group Admin Cannot Access Other Group Data
- **Setup**: Admin2 manages Group B
- **Action**: Try to access Group A members via direct URL
- [ ] Request blocked
- [ ] Cannot see Group A admin screen
- [ ] Redirect to only managed group

#### Test 4.2.3: Member Multi-Group Isolation
- **Setup**: Member1 in Group A and Group B
- **Action**: 
  1. View contributions in Group A
  2. Switch to Group B
  3. Check contributions
- [ ] Group A contributions NOT visible in Group B
- [ ] Group B contributions NOT visible when viewing Group A
- [ ] No cross-group data leakage

#### Test 4.2.4: Suspended Member Cannot Modify
- **Setup**: Member5 (Busisiwe) is suspended
- **Action**: Try to:
  - [ ] Add beneficiary → Blocked
  - [ ] Upload document → Blocked
  - [ ] Request payout → Blocked
  - [ ] Request loan → Blocked
- **Expected**: All modifications prevented

---

## ✅ Phase 5: Database Integrity & Seeder Validation (LOW PRIORITY)

**Goal**: Verify database state consistency and seeder correctness

### 5.1 Seeder Execution & Verification

**Test Command** (after deploying to Supabase):
```sql
-- Verify 30 groups created
SELECT COUNT(*) as group_count FROM groups;
-- Expected: 30

-- Verify 300+ members created
SELECT COUNT(*) as member_count FROM members;
-- Expected: 300+

-- Verify platform admin exists
SELECT id, email, role FROM auth.users 
WHERE email = 'torrymsimango@gmail.com';

-- Verify 4 named personas
SELECT email, status_name FROM members 
ORDER BY created_at DESC 
LIMIT 6;
-- Expected: member3-6 emails visible

-- Verify contribution records for Nompumelelo
SELECT COUNT(*) as contribution_count 
FROM contributions 
WHERE user_id IN (
  SELECT id FROM auth.users WHERE email = 'member3@test.com'
);
-- Expected: ~22 contributions

-- Verify beneficiaries for Nompumelelo
SELECT COUNT(*) as beneficiary_count 
FROM beneficiaries 
WHERE user_id IN (
  SELECT id FROM auth.users WHERE email = 'member3@test.com'
);
-- Expected: 3
```

### 5.2 Data Consistency Checks

**Test Cases**:

#### Test 5.2.1: Groups Member Count Accuracy
```sql
-- For each group, verify current_members matches actual count
SELECT 
  g.id, 
  g.name,
  g.current_members as expected_count,
  COUNT(m.id) as actual_count
FROM groups g
LEFT JOIN members m ON g.id = m.group_id AND m.status_name NOT IN ('SUSPENDED', 'PENDING_PAYMENT')
GROUP BY g.id, g.name, g.current_members
HAVING g.current_members != COUNT(m.id);
-- Expected: 0 rows (all counts match)
```

#### Test 5.2.2: Group Balance Accuracy
```sql
-- For each group, verify balance matches sum of contributions
SELECT 
  g.id, 
  g.name,
  COALESCE(g.balance, 0) as expected_balance,
  COALESCE(SUM(c.amount), 0) as actual_balance
FROM groups g
LEFT JOIN contributions c ON g.id = c.group_id AND c.status = 'paid'
GROUP BY g.id, g.name, g.balance
HAVING COALESCE(g.balance, 0) != COALESCE(SUM(c.amount), 0);
-- Expected: 0 rows (all balances match)
```

#### Test 5.2.3: Member Total Paid Accuracy
```sql
-- For each member, verify total_paid matches sum of contributions
SELECT 
  m.id, 
  m.email,
  COALESCE(m.total_paid, 0) as expected_total,
  COALESCE(SUM(c.amount), 0) as actual_total
FROM members m
LEFT JOIN contributions c ON m.id = c.user_id AND c.status = 'paid'
GROUP BY m.id, m.email, m.total_paid
HAVING COALESCE(m.total_paid, 0) != COALESCE(SUM(c.amount), 0);
-- Expected: 0 rows (all totals match)
```

#### Test 5.2.4: No Orphaned Records
```sql
-- Check for contributions without valid member
SELECT COUNT(*) FROM contributions 
WHERE user_id NOT IN (SELECT id FROM members);
-- Expected: 0

-- Check for beneficiaries without valid member
SELECT COUNT(*) FROM beneficiaries 
WHERE user_id NOT IN (SELECT id FROM members);
-- Expected: 0

-- Check for loans without valid member
SELECT COUNT(*) FROM loans 
WHERE user_id NOT IN (SELECT id FROM members);
-- Expected: 0
```

### 5.3 Seeder Documentation Verification

**Test Cases**:

#### Test 5.3.1: Audit Log Contains Seed Marker
```sql
SELECT * FROM audit_logs 
WHERE entity_type = 'SEED_COMPLETE' 
ORDER BY created_at DESC 
LIMIT 1;
-- Expected: 
-- - entry_type: 'SEED_COMPLETE'
-- - metadata contains: 'v2.1', '4 personas', '30 groups', '300+ members'
```

#### Test 5.3.2: AGENTS.md Updated with Personas
- [ ] AGENTS.md documents 4 personas
- [ ] Each has scenario description
- [ ] Test credentials listed

---

## 🚀 Test Execution Order

### Quick Path (2 hours)
```
1. Phase 1 (20 min) - Run tests + manual UI
2. Phase 2 (60 min) - Test all 4 personas
3. Phase 5 (40 min) - Quick DB checks
```

### Full Path (4-5 hours)
```
1. Phase 1 (30 min)
2. Phase 2 (90 min)
3. Phase 3 (30 min)
4. Phase 4 (30 min)
5. Phase 5 (60 min)
```

---

## 📝 Test Execution Log

Use this section to document test results:

### Phase 1: Suspend Button Logic
- [ ] Code review complete
- [ ] Unit tests passing (`./gradlew test`)
- [ ] Manual UI testing complete (10 test cases)
- [ ] Integration testing complete
- **Result**: ✅ PASS / ❌ FAIL

### Phase 2: Member Personas
- [ ] Database verification queries run
- [ ] Nompumelelo journey tested (7 test cases)
- [ ] Sipho journey tested (5 test cases)
- [ ] Busisiwe journey tested (5 test cases)
- [ ] Mandla journey tested (6 test cases)
- **Result**: ✅ PASS / ❌ FAIL

### Phase 3: Analytics
- [ ] Analytics dashboard loads
- [ ] All 30 groups visible
- [ ] Fee management functional
- [ ] Filters working
- **Result**: ✅ PASS / ❌ FAIL

### Phase 4: RLS/Auth
- [ ] Platform admin access verified
- [ ] Group admin scope verified
- [ ] Member scope verified
- [ ] Data isolation confirmed
- **Result**: ✅ PASS / ❌ FAIL

### Phase 5: Database
- [ ] Seeder verification queries passed
- [ ] Data consistency checks passed
- [ ] No orphaned records
- [ ] Audit log updated
- **Result**: ✅ PASS / ❌ FAIL

---

## 🎯 Sign-Off Criteria

All of the following must be true for release:

- [ ] Phase 1: 10/10 test cases pass
- [ ] Phase 2: All 4 personas test successfully
- [ ] Phase 3: Analytics & fee management working
- [ ] Phase 4: RLS/Auth properly enforced
- [ ] Phase 5: Database integrity verified
- [ ] No critical bugs introduced
- [ ] All error messages user-friendly
- [ ] Performance acceptable (< 2s load times)
- [ ] No data leakage between groups
- [ ] Suspend/unsuspend changes atomic

---

## 📞 Troubleshooting Guide

### Common Issues & Solutions

#### Suspend Button Tests Failing
**Problem**: Button state not synchronizing
**Check**: 
- [ ] `updateGroupSuspensionState()` method exists
- [ ] Both `isPlatformSuspended` and `feeStatus` updated
- [ ] No race conditions in state updates

#### Member Persona Login Fails
**Problem**: "Invalid credentials" error
**Check**:
- [ ] Test data seeder executed successfully
- [ ] Email exactly matches: member3@test.com, member4@test.com, etc.
- [ ] Password is "password123"
- [ ] LocalProperties includes Supabase keys

#### Database Queries Return 0
**Problem**: No groups/members found
**Check**:
- [ ] Seeder script executed (`seed_30_groups_300_members.sql`)
- [ ] Connected to correct database environment
- [ ] Check audit logs for SEED_COMPLETE marker

#### UI Doesn't Reflect Database Changes
**Problem**: Stale data in Compose UI
**Check**:
- [ ] Auto-refresh is enabled
- [ ] Flow is properly collected with `collectAsState()`
- [ ] State reset on navigation
- [ ] No cached values

---

## ✨ Success Criteria

When all phases complete successfully:

✅ **Suspend button logic verified end-to-end**  
✅ **All 4 personas tested with realistic journeys**  
✅ **Platform analytics working with correct metrics**  
✅ **RLS policies enforcing role-based access**  
✅ **Database integrity maintained with 300+ members**  
✅ **No critical regressions introduced**  
✅ **Production readiness confirmed**  

---

## 📊 Coverage Summary

| Phase | Priority | Coverage | Status | Sign-Off |
|-------|----------|----------|--------|----------|
| 1. Suspend Button | HIGH | 100% | ⬜ Pending | ⬜ |
| 2. Member Personas | HIGH | 100% | ⬜ Pending | ⬜ |
| 3. Analytics | MEDIUM | 90% | ⬜ Pending | ⬜ |
| 4. RLS/Auth | MEDIUM | 95% | ⬜ Pending | ⬜ |
| 5. Database | LOW | 100% | ⬜ Pending | ⬜ |

---

*Testing Plan Created: May 4, 2026*  
*Target Completion: May 4, 2026*  
*Status: 🚀 Ready to Execute*

