# Database ↔ Application Sync Summary
**Date:** April 19, 2026
**Database Schema Version:** 2.2
**Room Database Version:** 33

## Changes Made

### 1. Domain Models (`Models.kt`)

#### Contribution Model
- Added `policyId` field to match `policy_id` FK in schema
- Added `paymentMethod` field to match `payment_method` column

```kotlin
data class Contribution(
    ...
    @SerialName("policy_id") val policyId: String? = null,
    @SerialName("payment_method") val paymentMethod: String = "yoco",
    ...
)
```

#### PlatformFee Model  
- Updated to match actual database columns exactly
- Added `paidAt` and `transactionId` fields
- Removed non-existent `memberCount` and `ratePerMember` fields

```kotlin
data class PlatformFee(
    val id: String? = null,
    val groupId: String = "",
    val feeType: String = "monthly",  // 'registration' or 'monthly'
    val amount: Double = 0.0,
    val dueDate: String? = null,
    val status: AdminFeeState = AdminFeeState.DUE,
    val paidAt: String? = null,
    val transactionId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
```

#### PaymentType Enum
- Added `REGISTRATION` type to match `'registration'` in database

```kotlin
enum class PaymentType {
    JOINING_FEE, CONTRIBUTION, LATE_FEE, PLATFORM_FEE, 
    CLAIM, CUSTOM, REGISTRATION  // ← NEW
}
```

### 2. Room Database (`SanibonaniDatabase.kt`)

#### ContributionEntity
- Added `payment_method` column

```kotlin
data class ContributionEntity(
    ...
    @ColumnInfo(name = "payment_method") val paymentMethod: String = "yoco",
    ...
)
```

#### Database Version
- Bumped from **32** to **33**

### 3. Migrations (`Migrations.kt`)

#### New Migration 32 → 33
```kotlin
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE contributions ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'yoco'")
    }
}
```

### 4. Mappers (`Mappers.kt`)

#### Contribution Mappers
- Updated `toEntity()` to include `policyId` and `paymentMethod`
- Updated `toModel()` to include `policyId` and `paymentMethod`

### 5. Build System

#### Created `gradlew.bat`
- Added Windows batch script for Gradle wrapper (was missing)

---

## Schema Alignment Summary

| Supabase Table | Kotlin Model | Room Entity | Status |
|---------------|--------------|-------------|--------|
| `groups` | `Group` | `GroupEntity` | ✅ Synced |
| `members` | `Member` | `MemberEntity` | ✅ Synced |
| `beneficiaries` | `Beneficiary` | `BeneficiaryEntity` | ✅ Synced |
| `contributions` | `Contribution` | `ContributionEntity` | ✅ Synced |
| `payments` | `Payment` | `PaymentEntity` | ✅ Synced |
| `payouts` | `PayoutRequest` | `PayoutEntity` | ✅ Synced |
| `notifications` | `AppNotification` | `NotificationEntity` | ✅ Synced |
| `member_documents` | `MemberDocument` | `MemberDocumentEntity` | ✅ Synced |
| `platform_fees` | `PlatformFee` | *(No local cache)* | ✅ Synced |
| `policies` | *(Not yet)* | *(Not yet)* | ⚠️ Future |
| `profiles` | `UserProfile` | *(Auth layer)* | ✅ Synced |
| `group_actuarial_metrics` | `ActuarialMetrics` | *(No local cache)* | ✅ Synced |

---

## Testing Checklist

After building the app:

1. [ ] **App launches** without Room migration crashes
2. [ ] **Contributions load** from Supabase with `payment_method` field
3. [ ] **Payment recording** creates contributions with correct `type` values
4. [ ] **Platform fee** records are created/read correctly
5. [ ] **Group creation** saves all fields including `township`, `latitude`, `longitude`

---

## Notes

- The `policies` table exists in Supabase but is not actively used in the app yet
- PlatformFee is stored in Supabase only (no local Room cache) - this is intentional for platform admin features
- The `updated_at` field in Room entities uses `Long` (epoch millis) for local tracking, while Supabase uses `TIMESTAMPTZ` strings

