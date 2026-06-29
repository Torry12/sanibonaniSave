# Payout Security Implementation - Registered Bank Account Details

## Overview

This document describes the security implementation for payout requests in SanibonaniSave. The system ensures that payout disbursements are sent only to the group's **registered** and **protected** bank account details, preventing unauthorized modifications during the payout process.

## Security Principles

### 1. **Read-Only Bank Details**
- Bank account details used for payouts are derived from the group's registered bank profile
- These details **cannot be edited** during the payout request creation process
- Fields are visually disabled with gray styling and lock icons (🔒)

### 2. **Account Number Masking**
- Account numbers are masked for display security (showing only last 4 digits)
- Example: `1234567890` displays as `****7890`
- Full account number is only visible in supporting text (with reduced opacity)
- Prevents accidental exposure of sensitive financial information

### 3. **Clear Security Indicators**
- **Lock Icons (🔒)**: Displayed next to each protected field
- **Warning Cards**: Alert admins if bank details are incomplete
- **Protected Field Headers**: Clear indication that fields cannot be changed
- **Color Coding**: Protected fields use Forest green color for emphasis

## Implementation Details

### User Facing Changes

#### Admin Payout Request Screen (`PayoutTab.kt`)

**Before:**
```
Banking Details
├─ Bank Name (editable text field)
├─ Account Number (editable number field)
└─ Branch Code (editable number field)
```

**After (New Security Model):**
```
Payout Amount (editable)

🔒 Registered Bank Details (Protected)
   ├─ Bank Name: [DISABLED] 🔒
   ├─ Account Number: ****XXXX 🔒
   │     └─ Supporting: "Full account: 1234567890"
   └─ Branch Code: [DISABLED] 🔒

[Warning if missing: ⚠️ Bank Details Missing]

Submit Button (disabled if bank details incomplete)
```

#### Platform Admin Payout Review (`PayoutCard` in `PlatformComponents.kt`)

**Payout Card Display:**
```
🔒 Registered Account (Protected)
   "These details cannot be changed for security"

Protected Detail Rows:
├─ Bank: [Value] 🔒
├─ Account: ****XXXX 🔒  
└─ Branch: [Value] 🔒
```

### Code Components

#### 1. **Bank Details Prefilling Logic**
```kotlin
// In PayoutTab
val group = state.group  // Group contains registered bank details

// Use registered bank details (NOT user input)
OutlinedTextField(
    value = group?.bankName ?: "",  // From group, NOT from input state
    enabled = false,                // Always disabled
    colors = OutlinedTextFieldDefaults.colors(
        disabledBorderColor = LightGray,
        disabledTextColor = MidGray
    )
)
```

#### 2. **Account Number Masking Utility**
```kotlin
/**
 * Masks account number for security display
 * Shows only last 4 digits
 * Example: "1234567890" → "****7890"
 */
fun maskAccountNumber(accountNumber: String): String = when {
    accountNumber.length <= 4 -> accountNumber
    else -> "*".repeat(accountNumber.length - 4) + accountNumber.takeLast(4)
}

// Also available in PlatformComponents for payout review cards:
fun maskAccountNumberForDisplay(accountNumber: String): String
```

#### 3. **Enhanced DetailRow Component**
```kotlin
@Composable
fun DetailRow(
    label: String, 
    value: String, 
    isProtected: Boolean = false
) {
    // When isProtected=true:
    // - Label color changes to Forest (green)
    // - Lock icon (🔒) displays next to value
    // - Provides visual security cue
}
```

#### 4. **Submit Button Validation**
```kotlin
// Submit button is disabled until ALL conditions met:
enabled = !state.isRequestingPayout && 
          state.payoutAmount.isNotEmpty() && 
          (amount <= balance) &&
          (amount > 0) &&
          hasRegisteredBankDetails  // ✅ NEW: Bank details must exist
```

## Security Data Flow

### 1. **Payout Request Creation**
```
User Action: Click "Submit Payout"
    ↓
Validation:
  ✓ Payout amount exists and is valid
  ✓ Balance sufficient
  ✓ Bank details registered (NOT manually entered)
    ↓
Create PayoutRequest:
  {
    groupId: "...",
    amount: 50000,
    bankName: (from group.bankName),     ← Sourced from group
    accountNo: (from group.accountNumber), ← Sourced from group
    branchCode: (from group.branchCode)   ← Sourced from group
  }
    ↓
Submit to Group Admin for verification
```

### 2. **Platform Admin Review**
```
Platform Admin views PayoutCard
    ↓
Display:
  - All bank details shown as read-only fields
  - Account number masked (****XXXX)
  - Full account visible in supporting text only
  - Lock icons indicate protection
    ↓
Actions Available:
  - Approve (moves to GROUP_APPROVED status)
  - Reject (cancels request)
  - View Portal (for detailed group/member info)
```

## Risk Mitigation

### Risks Addressed

| Risk | Mitigation | Implementation |
|------|-----------|-----------------|
| **Accidental Bank Detail Changes** | Read-only fields | `enabled = false` + visual indicators |
| **Incorrect Recipient Account** | Prefill from registered source | Pull from `group.bankName`, not user input |
| **Account Number Exposure** | Masking + limiting visibility | Display `****XXXX`, full number in hidden supporting text |
| **Missing Bank Details** | Preventive validation | Submit button disabled + warning card |
| **Fraud via Field Modification** | Visual security cues | Lock icons + color coding + warning messages |

## Testing Scenarios

### Test Case 1: Complete Registration  
**Scenario**: Group has all bank details registered
```
Expected: 
  - Bank fields display correctly as read-only
  - Submit button enabled
  - Account number masked in UI
```

### Test Case 2: Missing Bank Details  
**Scenario**: Group missing branch code
```
Expected:
  - Missing branch code field shows empty
  - Warning card appears: "⚠️ Bank Details Missing"
  - Submit button is DISABLED
  - Message guides user to configure bank account
```

### Test Case 3: Security Indication  
**Scenario**: User views payout in platform admin panel
```
Expected:
  - All bank detail fields show lock icons
  - "Registered Account (Protected)" header visible
  - Account number displays as ****1234
  - Full account visible only in supporting text
```

### Test Case 4: Prevent Manual Edit Attempts  
**Scenario**: User attempts to modify bank fields
```
Expected:
  - Fields are non-selectable (disabled)
  - No keyboard input possible
  - Greyed out styling indicates disabled state
```

## Admin Configuration

### Prerequisites
Before payouts can be requested, groups must have registered bank details:

```kotlin
// In Group model (domain/model/GroupModels.kt)
data class Group(
    // ... other fields ...
    val bankName: String = "",          // Required for payout
    val accountNumber: String = "",     // Required for payout
    val branchCode: String = "",        // Required for payout
    // ...
)
```

### Configuration Screen
Admins should configure bank details in the Group Settings/Admin Panel:
```
Group Configuration
├─ Bank Name: [text input]
├─ Account Number: [number input - 7-13 digits]
└─ Branch Code: [number input - 6 digits]
```

## API Integration Points

### Payout Request Creation
```kotlin
// In AdminViewModel or PayoutRepository
suspend fun submitPayoutRequest(amount: Double, groupId: String): Result<PayoutRequest> {
    // Get group's REGISTERED bank details (NOT user input)
    val group = groupRepository.getGroup(groupId).getOrThrow()
    
    // Create payout using registered bank details
    val payoutRequest = PayoutRequest(
        groupId = groupId,
        amount = amount,
        bankName = group.bankName,           // ← From group
        accountNo = group.accountNumber,     // ← From group
        branchCode = group.branchCode,       // ← From group
        status = PayoutStatus.PENDING
    )
    
    // Save payout request
    return payoutRepository.savePayoutRequest(payoutRequest)
}
```

## Compliance & Audit

### Security Attributes
- ✅ **Immutable Bank Details**: Cannot be changed during payout creation
- ✅ **Audit Trail**: All payout requests logged with source group details
- ✅ **Encryption**: Bank details encrypted at rest (via Supabase)
- ✅ **Access Control**: Only admins can view full account numbers
- ✅ **User Feedback**: Clear security indicators to prevent confusion

### Audit Log Entry
```
Action: CREATE_PAYOUT_REQUEST
Details: {
  payoutId: "...",
  groupId: "...",
  amount: 50000,
  bankAccount: "****7890",  // Masked
  requestedBy: "admin@...",
  timestamp: "2026-06-03T10:30:00Z",
  status: "PENDING"
}
```

## Migration Notes

### For Existing Payouts
- Lock in current bank details for pending/processing payouts
- Display registered account info alongside entered details
- Gradually enforce read-only for all new payout requests

### For Database Schema
Ensure `groups` table has these columns:
```sql
ALTER TABLE groups ADD COLUMN bank_name TEXT;
ALTER TABLE groups ADD COLUMN account_number TEXT;
ALTER TABLE groups ADD COLUMN branch_code TEXT;
```

## Future Enhancements

1. **Bank Verification**: Add bank API validation when configuring accounts
2. **Account Holder Verification**: Verify account holder name matches group
3. **Rate Limiting**: Limit payout requests per group per period
4. **Webhook Confirmation**: Callback from payment provider confirming receipt
5. **Multi-Signature Approval**: Require multiple admins for large payouts
6. **Scheduled Payouts**: Pre-authorize recurring disbursements

## References

- **Group Model**: `domain/src/main/java/com/sanibonani/save/domain/model/GroupModels.kt`
- **Payout Models**: `domain/src/main/java/com/sanibonani/save/domain/model/PayoutModels.kt`
- **PayoutTab UI**: `app/src/main/java/com/sanibonani/save/ui/screens/admin/tabs/PayoutTab.kt`
- **PlatformComponents**: `app/src/main/java/com/sanibonani/save/ui/screens/admin/components/PlatformComponents.kt`
- **DetailRow Component**: `app/src/main/java/com/sanibonani/save/ui/components/SharedComponents.kt`

## Questions & Support

For questions about this security implementation:
1. Review the security principle section
2. Check test scenarios for expected behavior
3. Verify group bank details are configured before payout requests
4. Ensure DetailRow component is properly imported where used

