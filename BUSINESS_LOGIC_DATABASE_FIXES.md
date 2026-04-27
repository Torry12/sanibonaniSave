# 🔧 BUSINESS LOGIC & DATABASE SCHEMA FIXES — April 1, 2026

## CRITICAL ISSUES IDENTIFIED

### 1. **Payment Recording Business Logic Bug** 🔴 CRITICAL
**Location**: PaymentViewModel.kt lines 62-94

**Issue**: When recording joining fee payment, the code:
1. Records payment with `PaymentType.JOINING_FEE`
2. ALSO creates a Contribution record
3. This creates duplicate records and logic confusion

**Problems**:
- Payments table and Contributions table are duplicating data
- Joining fee should NOT create contribution record
- Status transitions are conflicting (member set to PROBATION twice)

**Fix Required**: Separate payments from contributions clearly

---

### 2. **Member Status Transition Logic Error** 🔴 CRITICAL
**Location**: PaymentViewModel.kt line 88

**Code**:
```kotlin
memberRepo.updateMemberStatus(member.id, MemberStatus.PROBATION)
```

**Issue**: After joining fee payment, member status is set to PROBATION
- But member should ALREADY be in PROBATION (from registration)
- This logic is redundant and confusing
- No validation that member isn't already ACTIVE

**Fix Required**: Remove redundant status update, validate member state first

---

### 3. **Group Balance Increment Missing Validation** 🔴 HIGH
**Location**: PaymentViewModel.kt line 90 & GroupRepository.kt

**Issue**: `incrementGroupBalance()` doesn't:
- Check if group exists
- Validate amount > 0
- Handle race conditions (concurrent updates)
- Sync to Room cache afterward

**Fix Required**: Add validation and proper sync

---

### 4. **Contribution Status Mismatch** 🔴 HIGH
**Location**: PaymentViewModel.kt lines 78-85

**Issue**: Creating Contribution with:
```kotlin
status = ContributionStatus.PAID
paidAt = timestampStr
dueDate = timestampStr  // Wrong! Should be future date
```

**Problem**: Due date should be next month, not today  
**Fix Required**: Calculate proper due date based on group's payment_due_day

---

### 5. **Payment Entity Missing Required Fields** 🔴 CRITICAL
**Location**: SanibonaniDatabase.kt PaymentEntity (line 166-178)

**Missing Columns**: 
- `member_id` can be NULL for platform fees (OK)
- But no `created_at_timestamp` (uses only `updated_at`)
- Missing `payment_processed_timestamp` distinction

**Fix Required**: Add proper timestamp columns for audit trail

---

### 6. **Contribution Payment Recording Logic** 🔴 CRITICAL
**Location**: PaymentViewModel.kt lines 97-147

**Issue**: For monthly contributions:
- Uses direct `supabaseManager.client.postgrest` instead of repository
- Doesn't use `paymentRepo.recordPayment()`
- Creates multiple records without transactional consistency
- No retry logic if one step fails

**Fix Required**: Use repository layer consistently

---

### 7. **Database Sync Issue: Created Timestamps** 🔴 MEDIUM
**Location**: Models.kt vs SanibonaniDatabase.kt

**Model Side** (Models.kt):
```kotlin
@SerialName("created_at") val createdAt: String? = null
```

**Entity Side** (SanibonaniDatabase.kt):
```kotlin
@ColumnInfo(name = "created_at") val createdAt: String?
```

**Issue**: 
- Supabase: `created_at` is timestamp (auto-generated)
- Room: Storing as String nullable (OK but risky)
- No validation that Supabase-generated dates match Room

**Fix Required**: Ensure timestamp consistency in sync flow

---

### 8. **Group Balance Race Condition** 🔴 HIGH
**Location**: GroupRepository.kt incrementGroupBalance()

```kotlin
override suspend fun incrementGroupBalance(groupId: String, amount: Double): Result<Unit> = runCatching {
    val group = getGroupById(groupId).getOrThrow()
    updateGroupBalance(groupId, group.balance + amount).getOrThrow()
}
```

**Issue**: Between read and write, another process could update balance
- Not atomic
- Causes lost updates in concurrent scenarios
- Should use PostgreSQL `UPDATE groups SET balance = balance + $amount`

**Fix Required**: Use atomic SQL operation

---

### 9. **Member Total Contributions Increment Issue** 🔴 HIGH
**Location**: MemberRepository.kt incrementTotalContributions()

**Issue**: Same race condition as group balance
- Reads then writes (non-atomic)
- Can lose updates

**Fix Required**: Use atomic SQL increment

---

### 10. **Payment Type Validation Missing** 🔴 MEDIUM
**Location**: PaymentViewModel.kt processPayment()

**Issue**: `when (type)` only handles "registration" and "joining_fee"
- String-based type checking (fragile)
- No validation against enum values
- "else" branch doesn't specify what happens

**Fix Required**: Use PaymentType enum, validate upfront

---

### 11. **Contribution Status Never Set to OVERDUE** 🔴 MEDIUM
**Location**: No scheduled job to update overdue contributions

**Issue**: ContributionStatus.OVERDUE never gets set
- Contributions stay PAID even after due date
- Admin can't see which members are late
- No late fee calculation triggered

**Fix Required**: Add WorkManager job to calculate overdue status

---

### 12. **Member Probation End Date Calculation** 🟡 HIGH
**Location**: GroupViewModel.kt (missing) / MemberRepository.kt

**Issue**: When member joins, `probation_end_at` is set somewhere
- But NOT set in submitGroup() ViewModel
- Where is the date calculated? Room mapper? Repository?
- No validation it's exactly 3 months from now

**Fix Required**: Explicit probation end date calculation in repository

---

### 13. **Missing Contribution Amount Defaults** 🟡 MEDIUM
**Location**: PaymentViewModel.kt lines 78-85

**Issue**: Contribution created with amount but no validation:
- What if amount is 0?
- What if amount > group.monthlyContribution?
- No enum for contribution type

**Fix Required**: Validate amounts, add type field

---

### 14. **Payment Method Not Captured** 🟡 MEDIUM
**Location**: PaymentViewModel.kt processPayment()

**Issue**: Creates Payment with default:
```kotlin
paymentMethod = PaymentMethod.YOCO  // Hardcoded!
```

**Problem**: Should be dynamic based on payment type
- Platform fee might be BANK_TRANSFER
- Joining fee might be CASH

**Fix Required**: Parameterize payment method

---

### 15. **Missing Transaction Timestamp Tracking** 🟡 HIGH
**Location**: Multiple repositories

**Issue**: No distinction between:
- `created_at`: When record created in DB
- `processed_at`: When payment actually processed
- `completed_at`: When verified by YoCo

**Fix Required**: Add proper timestamp fields for audit trail

---

## 🔧 FIXES TO APPLY

### Fix 1: Add Atomic Balance Update
**File**: GroupRepository.kt

Replace incrementGroupBalance with:
```kotlin
override suspend fun incrementGroupBalance(groupId: String, amount: Double): Result<Unit> = runCatching {
    if (amount <= 0) throw IllegalArgumentException("Amount must be positive")
    
    supabase.postgrest["groups"].update(buildJsonObject {
        put("balance", 0) // Will be overridden by PostgreSQL function
    }) { 
        filter { eq("id", groupId) }
        // Use raw SQL: UPDATE groups SET balance = balance + amount WHERE id = groupId
    }
    
    getGroupById(groupId).onSuccess { group ->
        db.groupDao().upsertGroup(group.toEntity())
    }
}
```

### Fix 2: Add Atomic Contribution Increment
**File**: MemberRepository.kt

```kotlin
override suspend fun incrementTotalContributions(memberId: String, groupId: String): Result<Unit> = runCatching {
    supabase.postgrest["members"].update(buildJsonObject {
        // Raw SQL: UPDATE members SET total_contributions = total_contributions + 1
    }) { 
        filter { eq("id", memberId) }
    }
    
    getMemberById(memberId).onSuccess { member ->
        db.memberDao().upsertMember(member.toEntity())
    }
}
```

### Fix 3: Separate Payment from Contribution
**File**: PaymentViewModel.kt

Remove duplicate contribution creation for joining fees.

### Fix 4: Add Proper Contribution Due Date Calculation
**File**: PaymentRepository.kt / MemberRepository.kt

```kotlin
fun calculateNextContributionDueDate(groupId: String, paymentDueDay: Int): LocalDate {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val currentMonth = today.monthNumber
    val currentYear = today.year
    
    val dueDate = try {
        LocalDate(currentYear, currentMonth, paymentDueDay)
    } catch (e: Exception) {
        LocalDate(currentYear, currentMonth, 28) // Fallback to 28th if invalid day
    }
    
    return if (dueDate <= today) {
        // Due date already passed this month, set for next month
        val nextMonth = if (currentMonth == 12) 1 else currentMonth + 1
        val nextYear = if (currentMonth == 12) currentYear + 1 else currentYear
        try {
            LocalDate(nextYear, nextMonth, paymentDueDay)
        } catch (e: Exception) {
            LocalDate(nextYear, nextMonth, 28)
        }
    } else {
        dueDate
    }
}
```

### Fix 5: Add Overdue Status Calculation
**File**: New WorkManager Job

```kotlin
@HiltWorker
class ContributionOverdueWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val groupRepo: GroupRepository,
    private val memberRepo: MemberRepository
) : CoroutineWorker(appContext, workerParams) {
    
    override suspend fun doWork(): Result = try {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        
        // Get all DUE contributions with due_date < today
        val contributions = supabase.postgrest["contributions"].select {
            filter { lt("due_date", today.toString()) }
            filter { eq("status", "due") }
        }.decodeList<Contribution>()
        
        // Update to OVERDUE
        contributions.forEach { contrib ->
            supabase.postgrest["contributions"].update(buildJsonObject {
                put("status", "overdue")
            }) {
                filter { eq("id", contrib.id) }
            }
            
            // Trigger late fee calculation if needed
            if (group.lateFee > 0 && !contrib.lateFeesApplied) {
                applyLateFeesForContribution(contrib)
            }
        }
        
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
```

### Fix 6: Validate Payment Type Upfront
**File**: PaymentViewModel.kt

```kotlin
fun processPayment(
    type: PaymentType,  // Changed from String
    amount: Double,
    groupId: String,
    paymentMethod: PaymentMethod = PaymentMethod.YOCO
) {
    if (amount <= 0) {
        _state.update { it.copy(error = "Amount must be positive") }
        return
    }
    
    // Validation uses enum, not string matching
    when (type) {
        PaymentType.PLATFORM_FEE -> { /* ... */ }
        PaymentType.JOINING_FEE -> { /* ... */ }
        PaymentType.CONTRIBUTION -> { /* ... */ }
        else -> _state.update { it.copy(error = "Invalid payment type") }
    }
}
```

### Fix 7: Fix Member Status Logic
**File**: PaymentViewModel.kt

```kotlin
// After joining_fee payment:
memberRepo.getMemberByUserId(userId, groupId)
    .onSuccess { member ->
        // Validate member is in PROBATION already
        if (member.status != MemberStatus.PROBATION) {
            throw IllegalStateException("Member should be in PROBATION status, got: ${member.status}")
        }
        
        // Don't update status again, just record payment
        recordPaymentRecord(txId, amount, groupId, PaymentType.JOINING_FEE, timestampStr)
        groupRepo.incrementGroupBalance(groupId, amount)
        
        // Send notification
        notificationRepo.sendNotification(AppNotification(
            groupId = groupId,
            memberId = member.id,
            message = "Joining fee received!",
            triggerEvent = NotifEvent.PAYMENT_CONFIRMED
        ))
    }
}
```

### Fix 8: Add Payment Timestamp Distinction
**File**: SanibonaniDatabase.kt & Models.kt

Add fields:
```kotlin
data class PaymentEntity(
    // ...existing...
    @ColumnInfo(name = "created_at") val createdAt: String?,       // Record created
    @ColumnInfo(name = "processed_at") val processedAt: String?,   // YoCo processed
    @ColumnInfo(name = "verified_at") val verifiedAt: String?,     // Webhook confirmed
    @ColumnInfo(name = "completed_at") val completedAt: String?,   // Final state
)
```

### Fix 9: Use Repository for All Operations
**File**: PaymentViewModel.kt

Remove direct supabase calls, use repo:
```kotlin
// BEFORE (line 86):
supabaseManager.client.postgrest["contributions"].insert(contribution)

// AFTER:
val contrib = Contribution(
    memberId = member.id!!,
    groupId = groupId,
    amount = amount,
    status = ContributionStatus.PAID,
    paidAt = timestampStr,
    dueDate = calculateNextContributionDueDate(groupId, group.paymentDueDay)
)
// Use a proper repository method instead
```

### Fix 10: Add Probation End Date Calculation
**File**: MemberRepository.kt

```kotlin
fun registerMember(member: Member): Result<Member> = runCatching {
    val group = groupRepo.getGroupById(member.groupId).getOrThrow()
    
    val probationEndDate = Clock.System.now()
        .plus(group.probationMonths.toLong(), DateTimeUnit.MONTH, TimeZone.currentSystemDefault())
    
    val memberWithProbation = member.copy(
        joinedAt = Clock.System.now().toString(),
        probationEndAt = probationEndDate.toString(),
        status = MemberStatus.PROBATION
    )
    
    val created = supabase.postgrest["members"]
        .insert(memberWithProbation) { select() }
        .decodeSingle<Member>()
    
    db.memberDao().upsertMember(created.toEntity())
    created
}
```

---

## ✅ SUMMARY OF FIXES

| Issue | Severity | Status | Fix |
|-------|----------|--------|-----|
| Payment/Contribution Duplication | 🔴 CRITICAL | PENDING | Use separate recording |
| Member Status Redundant Update | 🔴 CRITICAL | PENDING | Validate before update |
| Balance Update Race Condition | 🔴 CRITICAL | PENDING | Use atomic SQL |
| Contribution Due Date Wrong | 🔴 HIGH | PENDING | Calculate properly |
| Missing Overdue Status | 🔴 HIGH | PENDING | Add WorkManager job |
| Direct Supabase Calls | 🔴 HIGH | PENDING | Use repositories |
| Payment Type Validation | 🟡 MEDIUM | PENDING | Use enums |
| Timestamp Tracking | 🟡 MEDIUM | PENDING | Add fields |
| Member Probation Calculation | 🟡 MEDIUM | PENDING | Explicit logic |

---

**Next Step**: Apply fixes to code files

