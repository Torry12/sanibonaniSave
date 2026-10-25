# Monetary Field Formatting Standard

**Effective Date**: June 3, 2026  
**Status**: ✅ IMPLEMENTED

---

## 🎯 Requirement

All monetary fields must display with exactly **2 decimal places**.

Examples:
- ✅ R1,234.50
- ✅ R0.99
- ✅ R100.00
- ❌ R1234.5 (missing trailing zero)
- ❌ R100 (no decimals)
- ❌ R1,234.5699 (too many decimals)

---

## 📚 Tools Available

### 1. MoneyFormatter Object (Recommended)

**Location**: `domain/utils/MoneyFormatter.kt`

```kotlin
// Format with currency symbol
MoneyFormatter.formatAsZAR(1234.56)        // Returns: "R1,234.56"
MoneyFormatter.formatAsZAR(BigDecimal("1234.56"))

// Format as plain decimal (no symbol)
MoneyFormatter.formatAsPlain(1234.56)      // Returns: "1234.56"
MoneyFormatter.formatAsPlain(BigDecimal("1234.56"))

// Format percentage
MoneyFormatter.formatAsPercentage(15.5)    // Returns: "15.50%"

// Custom currency symbol
MoneyFormatter.formatWithCurrency(100.00, "$")  // Returns: "$100.00"
```

### 2. Extension Functions (Most Convenient)

```kotlin
// On Double
val amount: Double = 1234.56
amount.formatZAR()          // Returns: "R1,234.56"
amount.formatPlain()        // Returns: "1234.56"
amount.formatPercent()      // Returns: "1234.56%"
amount?.formatZARSafe()     // Returns: "R1,234.56" or "R0.00" if null

// On BigDecimal
val bd: BigDecimal = BigDecimal("1234.56")
bd.formatZAR()              // Returns: "R1,234.56"
bd.formatPlain()            // Returns: "1234.56"
```

### 3. Money Math Functions (For Calculations)

**Location**: `domain/utils/MoneyMath.kt`

```kotlin
// Convert to proper monetary types
val amount: Double = 1234.5678
amount.roundMoneyToTwoDecimals()    // Returns: 1234.57
amount.toMoneyBigDecimal()          // Returns: BigDecimal("1234.57")

// Parse user input
"1234.56".parseMoneyAmountOrNull()  // Returns: BigDecimal("1234.56")
"invalid".parseMoneyAmountOrNull()  // Returns: null

// Validation
1234.56.isPositiveMoneyAmount()     // true (> 0)
0.00.isNonNegativeMoneyAmount()     // true (>= 0)
```

---

## 🔧 Usage Examples

### In Compose UI Text Fields

```kotlin
// ✅ CORRECT - Using extension function
val balance: Double = 1234.5
Text("Balance: ${balance.formatZAR()}")        // "Balance: R1,234.50"

// ✅ CORRECT - Using MoneyFormatter
Text("Balance: ${MoneyFormatter.formatAsZAR(balance)}")

// ❌ WRONG - Raw toString()
Text("Balance: $balance")                         // "Balance: 1234.5"

// ❌ WRONG - Manual formatting
Text("Balance: R${String.format("%.2f", balance)}")  // Works but inconsistent
```

### In Payment Screens

```kotlin
@Composable
fun PaymentScreen(amount: Double) {
    Column {
        // Amount display
        Text("Amount: ${amount.formatZAR()}")
        
        // Amount in text field
        TextField(
            value = amount.formatPlain(),  // No currency symbol in input
            onValueChange = { newValue ->
                val parsed = newValue.parseMoneyAmountOrNull()
                if (parsed != null) {
                    updateAmount(parsed.toDouble())
                }
            }
        )
        
        // Formatted for display after
        Text("Formatted: ${amount.formatZAR()}")
    }
}
```

### In Member/Admin Dashboard

```kotlin
@Composable
fun MemberCard(member: Member, contribution: Double, balance: Double) {
    Column {
        Text("Name: ${member.fullName}")
        Text("Monthly: ${contribution.formatZAR()}")
        Text("Balance: ${balance.formatZAR()}")
        
        // Group context
        val groupBalance = group?.balance ?: 0.0
        Text("Group Total: ${groupBalance.formatZAR()}")
    }
}
```

### In Calculations (Backend)

```kotlin
fun calculateFee(baseAmount: Double, feePercent: Double): Double {
    val base = baseAmount.toMoneyBigDecimal()
    val fee = base.multiply(BigDecimal(feePercent))
        .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    
    return fee.toDouble()  // Still 2 decimal places
}

// For display:
val feeAmount = calculateFee(1000.00, 10.0)
Text("Fee: ${feeAmount.formatZAR()}")  // "Fee: R100.00"
```

---

## 📋 Implementation Checklist

### For Existing Code

- [ ] Search for all `Text()` displaying monetary values
- [ ] Ensure each uses `.formatZAR()` or `.formatPlain()`
- [ ] Replace `toString()` calls on money with formatters
- [ ] Replace manual `String.format("%.2f", ...)` with formatters
- [ ] Test with edge cases: 0.00, 0.99, 999999.99

### For New Features

- [ ] All monetary model fields default to properly rounded values
- [ ] All monetary displays use `.formatZAR()` or `.formatPlain()`
- [ ] All monetary inputs validated with `parseMoneyAmountOrNull()`
- [ ] All calculations use `toMoneyBigDecimal()` to maintain precision
- [ ] All monetary comparisons use `MoneyFormatter.compareMoney()` or BigDecimal

### Data Layer

- [ ] Model fields storing money are `Double` with proper defaults
- [ ] Room entities round on read/write
- [ ] Supabase calls ensure 2 decimal precision
- [ ] API responses round on deserialization

---

## 🎨 Display Patterns

### Pattern 1: Currency Display
```kotlin
Text(amount.formatZAR())                    // R1,234.56
```

### Pattern 2: Plain Amount (No Symbol)
```kotlin
TextField(value = amount.formatPlain())     // 1234.56
```

### Pattern 3: Percentage
```kotlin
Text(percentage.formatPercent())            // 15.50%
```

### Pattern 4: Null Safety
```kotlin
Text(amount?.formatZARSafe() ?: "R0.00")   // Handles nulls gracefully
```

### Pattern 5: Comparison Display
```kotlin
if (MoneyFormatter.areEqual(amount1, amount2)) {
    Text("Amounts are equal")
}
```

---

## ✅ Verification

### Manual Testing Checklist

- [ ] Launch app and navigate to Member Dashboard
- [ ] Check all balance displays show xx.xx format
- [ ] Navigate to Payment screen
  - [ ] Amount field shows R#,###.xx
  - [ ] Calculated fees show R#.xx
  - [ ] Totals show R#,###.xx
- [ ] Check Admin Dashboard
  - [ ] Group balance displays R#,###.xx
  - [ ] Member contributions show R#.xx
  - [ ] Fee calculations show R#.xx
- [ ] Test edge cases
  - [ ] 0.00 displays as "R0.00"
  - [ ] 0.99 displays as "R0.99"
  - [ ] 999999.99 displays as "R999,999.99"
  - [ ] Very small amounts display with 2 decimals

### Automated Testing

```kotlin
@Test
fun testMoneyFormatterPrecision() {
    assertEquals("R1,234.50", (1234.5).formatZAR())
    assertEquals("R0.99", (0.99).formatZAR())
    assertEquals("R0.00", (0.0).formatZAR())
    assertEquals("R100.00", (100.0).formatZAR())
    assertEquals("15.50%", (15.5).formatPercent())
}
```

---

## 🚀 Migration Path

For existing code that doesn't use MoneyFormatter:

### Step 1: Identify Issues
```kotlin
// Find this...
Text("Amount: $amount")                    // ❌ Could be "Amount: 1234.5"

// Replace with this...
Text("Amount: ${amount.formatZAR()}")      // ✅ "Amount: R1,234.50"
```

### Step 2: Find & Replace (IDE)
```
Search:  Text\(".*\$.*amount
Replace: Use formatZAR() extension
```

### Step 3: Verify
- [ ] All monetary Text() fields use formatters
- [ ] All monetary user input fields validate with parseMoneyAmountOrNull()
- [ ] All calculations use BigDecimal or roundMoneyToTwoDecimals()

---

## 📊 Field List: All Monetary Fields

All these fields must always display with 2 decimal places:

### Group Model
- `joiningFee`
- `monthlyContribution`
- `lateFee`
- `balance`
- `goalAmount`
- `beneficiaryIncreasePct` (percentage)
- `loanInterestRate` (percentage)
- `loanMaxAmount`

### Member Model
- `monthlyContributionOverride`

### Contribution Model
- `amount`

### Payment Model
- All payment amounts

### Payout Model
- All payout amounts

### Loan Model
- All loan amounts, interest, balances

### Administrative Fees
- `platformFeeAmount`
- `registrationFee`

---

## 🔗 Related Files

- `domain/utils/MoneyFormatter.kt` - Main formatter (NEW)
- `domain/utils/MoneyMath.kt` - Math operations
- `domain/utils/CurrencyUtils.kt` - Deprecated (kept for backward compatibility)
- `data/utils/PaymentCalculator.kt` - Uses formatters internally

---

## ⚠️ Common Mistakes to Avoid

1. ❌ **Mixing formatters**
   - Don't use `String.format()` sometimes and `.formatZAR()` other times
   - Always use the centralized formatter

2. ❌ **Forgetting null checks**
   ```kotlin
   // ❌ WRONG
   val amount: Double? = null
   Text("Amount: ${amount.formatZAR()}")  // Crashes!
   
   // ✅ CORRECT
   Text("Amount: ${amount?.formatZARSafe()}")  // "Amount: R0.00"
   ```

3. ❌ **Rounding too early**
   ```kotlin
   // ❌ WRONG - loses precision in calculations
   val fee = (amount * 0.1).formatZAR().toDouble()
   
   // ✅ CORRECT - round only for display
   val fee = amount.toMoneyBigDecimal()
       .multiply(BigDecimal("0.1"))
       .toDouble()
   Text("Fee: ${fee.formatZAR()}")
   ```

4. ❌ **Not validating user input**
   ```kotlin
   // ❌ WRONG
   val amount = input.toDoubleOrNull()  // Could accept "1234.5678"
   
   // ✅ CORRECT
   val amount = input.parseMoneyAmountOrNull()  // Validates format
   ```

5. ❌ **Storing unrounded values**
   ```kotlin
   // ❌ WRONG
   member.balance = userInput.toDouble()  // Could be 1234.5678
   
   // ✅ CORRECT
   member.balance = userInput.parseMoneyAmountOrNull()
       ?.toDouble()
       ?.roundMoneyToTwoDecimals()
       ?: 0.0
   ```

---

## 📞 Support

For questions about monetary formatting:
1. Check this guide first
2. Review MoneyFormatter.kt code comments
3. Check MoneyMath.kt for calculation patterns
4. See PaymentScreen.kt for UI examples

---

**Last Updated**: June 3, 2026  
**Status**: ✅ COMPLETE & READY FOR DEPLOYMENT

